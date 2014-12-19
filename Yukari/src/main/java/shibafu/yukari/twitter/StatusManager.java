package shibafu.yukari.twitter;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.statusimpl.FakeStatus;
import shibafu.yukari.twitter.statusimpl.FavFakeStatus;
import shibafu.yukari.twitter.statusimpl.MetaStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusimpl.RespondNotifyStatus;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import shibafu.yukari.util.StringUtil;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterResponse;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager {
    private static LongSparseArray<PreformedStatus> receivedStatuses = new LongSparseArray<>(512);

    private static final boolean PUT_STREAM_LOG = false;

    public static final int UPDATE_FAVED = 1;
    public static final int UPDATE_UNFAVED = 2;
    public static final int UPDATE_DELETED = 3;
    public static final int UPDATE_DELETED_DM = 4;
    public static final int UPDATE_WIPE_TWEETS = 0xff;

    private static final String LOG_TAG = "StatusManager";

    //バイブレーションパターン
    private final static long[] VIB_REPLY = {450, 130, 140, 150};
    private final static long[] VIB_RETWEET = {150, 130, 300, 150};
    private final static long[] VIB_FAVED = {140, 100};

    private TwitterService service;
    private Suppressor suppressor;
    private List<AutoMuteConfig> autoMuteConfigs;

    private Context context;
    private NotificationManager notificationManager;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private SharedPreferences sharedPreferences;
    private Handler handler;

    //RT-Response Listen (Key:RTed User ID, Value:Response StandBy Status)
    private LongSparseArray<PreformedStatus> retweetResponseStandBy = new LongSparseArray<>();

    //キャッシュ
    private HashCache hashCache;

    //ステータス
    private boolean isStarted;

    //Streaming
    private List<StreamUser> streamUsers = new ArrayList<>();
    private Map<String, FilterStream> filterMap = new HashMap<>();
    private StreamListener listener = new StreamListener() {

        @Override
        public void onFavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onFavorite", String.format("f:%s s:%d", from.getUserRecord().ScreenName, status.getId()));
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_FAVED, new FavFakeStatus(status.getId(), true, user));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), UPDATE_FAVED, new FavFakeStatus(status.getId(), true, user)));
            }

            userUpdateDelayer.enqueue(status.getUser());
            userUpdateDelayer.enqueue(user);
            userUpdateDelayer.enqueue(user2);

            if (from.getUserRecord().NumericId == user.getId()) return;

            PreformedStatus preformedStatus = new PreformedStatus(status, from.getUserRecord());
            boolean[] mute = suppressor.decision(preformedStatus);
            boolean[] muteUser = suppressor.decisionUser(user);
            if (!(mute[MuteConfig.MUTE_NOTIF_FAV] || muteUser[MuteConfig.MUTE_NOTIF_FAV])) {
                showNotification(R.integer.notification_faved, preformedStatus, user);
            }
        }

        @Override
        public void onUnfavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onUnfavorite", String.format("f:%s s:%s", from.getUserRecord().ScreenName, status.getText()));
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user)));
            }
        }

        @Override
        public void onFollow(Stream from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(Stream from, DirectMessage directMessage) {
            userUpdateDelayer.enqueue(directMessage.getSender());
            for (StatusListener sl : statusListeners) {
                sl.onDirectMessage(from.getUserRecord(), directMessage);
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), directMessage));
            }

            boolean checkOwn = service.isMyTweet(directMessage) != null;
            if (!checkOwn) {
                showNotification(R.integer.notification_message, directMessage, directMessage.getSender());
            }
        }

        @Override
        public void onBlock(Stream from, User user, User user2) {

        }

        @Override
        public void onUnblock(Stream from, User user, User user2) {

        }

        @Override
        public void onStatus(final Stream from, Status status) {
            statusQueue.enqueue(from, status);
        }

        @Override
        public void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_DELETED, new FakeStatus(statusDeletionNotice.getStatusId()));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), UPDATE_DELETED, new FakeStatus(statusDeletionNotice.getStatusId())));
            }
        }

        public void onWipe(Stream from) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0)));
            }
        }

        @Override
        public void onDeletionNotice(Stream from, long directMessageId, long userId) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId)));
            }
        }

        private void showNotification(int category, TwitterResponse status, User actionBy) {
            TweetCommonDelegate delegate = TweetCommon.newInstance(status.getClass());

            int prefValue = 5;
            switch (category) {
                case R.integer.notification_replied:
                    prefValue = sharedPreferences.getInt("pref_notif_mention", 5);
                    break;
                case R.integer.notification_retweeted:
                    prefValue = sharedPreferences.getInt("pref_notif_rt", 5);
                    break;
                case R.integer.notification_faved:
                    prefValue = sharedPreferences.getInt("pref_notif_fav", 5);
                    break;
                case R.integer.notification_message:
                    prefValue = sharedPreferences.getInt("pref_notif_dm", 5);
                    break;
                case R.integer.notification_respond:
                    prefValue = sharedPreferences.getInt("pref_notif_respond", 0);
                    break;
            }
            NotificationType notificationType = new NotificationType(prefValue);

            if (notificationType.isEnabled()) {
                int icon = 0;
                Uri sound = null;
                String titleHeader = "", tickerHeader = "";
                long[] pattern = null;
                switch (category) {
                    case R.integer.notification_replied:
                        icon = R.drawable.ic_stat_reply;
                        titleHeader = "Reply from @";
                        tickerHeader = "リプライ : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
                        pattern = VIB_REPLY;
                        break;
                    case R.integer.notification_retweeted:
                        icon = R.drawable.ic_stat_retweet;
                        titleHeader = "Retweeted by @";
                        tickerHeader = "RTされました : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_rt");
                        pattern = VIB_RETWEET;
                        break;
                    case R.integer.notification_faved:
                        icon = R.drawable.ic_stat_favorite;
                        titleHeader = "Faved by @";
                        tickerHeader = "ふぁぼられ : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_fav");
                        pattern = VIB_FAVED;
                        break;
                    case R.integer.notification_message:
                        icon = R.drawable.ic_stat_message;
                        titleHeader = "Message from @";
                        tickerHeader = "DM : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
                        pattern = VIB_REPLY;
                        break;
                    case R.integer.notification_respond:
                        icon = R.drawable.ic_stat_reply;
                        titleHeader = "RT-Respond from @";
                        tickerHeader = "RTレスポンス : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
                        pattern = VIB_REPLY;
                        break;
                }
                if (notificationType.getNotificationType() == NotificationType.TYPE_NOTIF) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());
                    builder.setSmallIcon(icon);
                    builder.setContentTitle(titleHeader + actionBy.getScreenName());
                    builder.setContentText(delegate.getUser(status).getScreenName() + ": " + delegate.getText(status));
                    builder.setTicker(tickerHeader + actionBy.getScreenName());
                    if (notificationType.isUseSound()) {
                        builder.setSound(sound, AudioManager.STREAM_NOTIFICATION);
                    }
                    if (notificationType.isUseVibration()) {
                        vibrate(pattern, -1);
                    }
                    builder.setAutoCancel(true);
                    switch (category) {
                        case R.integer.notification_replied:
                        {
                            Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                    context.getApplicationContext(), R.integer.notification_replied, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            builder.setContentIntent(pendingIntent);

                            {
                                PreformedStatus ps = (PreformedStatus) status;
                                Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                                replyIntent.putExtra(TweetActivity.EXTRA_USER, ps.getRepresentUser());
                                replyIntent.putExtra(TweetActivity.EXTRA_STATUS, ((ps.isRetweet()) ? ps.getRetweetedStatus() : ps));
                                replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                                replyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                        ((ps.isRetweet()) ? ps.getRetweetedStatus().getUser().getScreenName()
                                                : ps.getUser().getScreenName()) + " ");
                                builder.addAction(R.drawable.ic_stat_reply, "返信", PendingIntent.getActivity(
                                                context.getApplicationContext(), R.integer.notification_replied, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                                );
                            }
                            break;
                        }
                        case R.integer.notification_respond:
                        {
                            Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                    context.getApplicationContext(), R.integer.notification_respond, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            builder.setContentIntent(pendingIntent);
                            break;
                        }
                        case R.integer.notification_message:
                        {
                            Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_DM);
                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                    context.getApplicationContext(), R.integer.notification_message, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            builder.setContentIntent(pendingIntent);

                            {
                                DirectMessage dm = (DirectMessage) status;
                                Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                                replyIntent.putExtra(TweetActivity.EXTRA_USER, findUserRecord(dm.getRecipient()));
                                replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                                replyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, dm.getSenderId());
                                replyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, dm.getSenderScreenName());
                                builder.addAction(R.drawable.ic_stat_message, "返信", PendingIntent.getActivity(
                                                context.getApplicationContext(), R.integer.notification_message, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                                );
                            }
                            break;
                        }
                    }
                    notificationManager.notify(category, builder.build());
                }
                else {
                    if (notificationType.isUseSound()) {
                        MediaPlayer mediaPlayer = MediaPlayer.create(context, sound);
                        mediaPlayer.start();
                    }
                    if (notificationType.isUseVibration()) {
                        vibrate(pattern, -1);
                    }
                    final String text = tickerHeader + actionBy.getScreenName() + "\n" +
                            delegate.getUser(status).getScreenName() + ": " + delegate.getText(status);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context.getApplicationContext(),
                                    text,
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
            }
        }

        // サイレントに設定しててもうっかりバイブが震えちゃうような
        // クソ端末でそのような挙動が起きないように
        private void vibrate(long[] pattern, int repeat) {
            switch (audioManager.getRingerMode()) {
                case AudioManager.RINGER_MODE_NORMAL:
                case AudioManager.RINGER_MODE_VIBRATE:
                    vibrator.vibrate(pattern, repeat);
                    break;
            }
        }

        private boolean deliver(StatusListener sl, Stream from) {
            if (sl.getStreamFilter() == null && from instanceof StreamUser) {
                return true;
            }
            else if (from instanceof FilterStream &&
                    ((FilterStream) from).getQuery().equals(sl.getStreamFilter())) {
                return true;
            }
            else return false;
        }

        private AuthUserRecord findUserRecord(User user) {
            for (AuthUserRecord userRecord : service.getUsers()) {
                if (userRecord.NumericId == user.getId()) {
                    return userRecord;
                }
            }
            return null;
        }

        private StatusQueue statusQueue = new StatusQueue();
        class StatusQueue {
            private BlockingQueue<Pair<Stream, Status>> queue = new LinkedBlockingQueue<>();

            public StatusQueue() {
                new Thread(new Worker(), "StatusQueue").start();
            }

            public void enqueue(Stream from, Status status) {
                queue.offer(new Pair<>(from, status));
            }

            class Worker implements Runnable {

                private void onStatus(final Stream from, Status status) {
                    if (from == null || status == null || status.getUser() == null || statusListeners == null) {
                        if (PUT_STREAM_LOG) Log.d(LOG_TAG, "onStatus, NullPointer!");
                        return;
                    }
                    if (PUT_STREAM_LOG) Log.d(LOG_TAG,
                            String.format("onStatus(Registered Listener %d): %s from %s :@%s %s",
                                    statusListeners.size(),
                                    StringUtil.formatDate(status.getCreatedAt()), from.getUserRecord().ScreenName,
                                    status.getUser().getScreenName(), status.getText()));
                    userUpdateDelayer.enqueue(status.getUser());
                    if (status.isRetweet()) {
                        userUpdateDelayer.enqueue(status.getRetweetedStatus().getUser());
                    }
                    AuthUserRecord user = from.getUserRecord();

                    //自分のツイートかどうかマッチングを行う
                    AuthUserRecord checkOwn = service.isMyTweet(status);
                    if (checkOwn != null) {
                        //自分のツイートであれば受信元は発信元アカウントということにする
                        user = checkOwn;
                    }

                    PreformedStatus preformedStatus = new PreformedStatus(status, user);

                    //オートミュート判定を行う
                    AutoMuteConfig autoMute = null;
                    for (AutoMuteConfig config : autoMuteConfigs) {
                        boolean match = false;
                        switch (config.getMatch()) {
                            case AutoMuteConfig.MATCH_EXACT:
                                match = preformedStatus.getText().equals(config.getQuery());
                                break;
                            case AutoMuteConfig.MATCH_PARTIAL:
                                match = preformedStatus.getText().contains(config.getQuery());
                                break;
                            case AutoMuteConfig.MATCH_REGEX:
                                try {
                                    Pattern pattern = Pattern.compile(config.getQuery());
                                    Matcher matcher = pattern.matcher(preformedStatus.getText());
                                    match = matcher.find();
                                } catch (PatternSyntaxException ignore) {}
                                break;
                        }
                        if (match) {
                            autoMute = config;
                        }
                    }
                    if (autoMute != null) {
                        Log.d(LOG_TAG, "AutoMute! : @" + preformedStatus.getSourceUser().getScreenName());
                        getDatabase().updateRecord(autoMute.getMuteConfig(preformedStatus.getSourceUser().getScreenName(), System.currentTimeMillis() + 3600000));
                        service.updateMuteConfig();
                    }

                    //ミュートチェックを行う
                    boolean[] mute = suppressor.decision(preformedStatus);
                    if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                        preformedStatus.setCensoredThumbs(true);
                    }

                    boolean muted = mute[MuteConfig.MUTE_TWEET_RTED] ||
                            (!preformedStatus.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                            (preformedStatus.isRetweet() && mute[MuteConfig.MUTE_RETWEET]);
                    PreformedStatus deliverStatus = new MetaStatus(preformedStatus, "realtime");
                    PreformedStatus standByStatus = retweetResponseStandBy.get(preformedStatus.getUser().getId());
                    if (new NotificationType(sharedPreferences.getInt("pref_notif_respond", 0)).isEnabled() &&
                            !preformedStatus.isRetweet() &&
                            standByStatus != null && !preformedStatus.getText().startsWith("@")) {
                        //Status is RT-Respond
                        retweetResponseStandBy.remove(preformedStatus.getUser().getId());
                        deliverStatus = new RespondNotifyStatus(preformedStatus, standByStatus);
                        showNotification(R.integer.notification_respond, deliverStatus, deliverStatus.getUser());
                    }
                    for (StatusListener sl : statusListeners) {
                        if (deliver(sl, from)) {
                            sl.onStatus(user, deliverStatus, muted);
                        }
                    }
                    deliverStatus = (deliverStatus instanceof MetaStatus)? new MetaStatus(preformedStatus, "queued") : deliverStatus;
                    for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                        if (deliver(e.getKey(), from)) {
                            e.getValue().offer(new EventBuffer(user, deliverStatus, muted));
                        }
                    }

                    if (status.isRetweet() &&
                            !mute[MuteConfig.MUTE_NOTIF_RT] &&
                            status.getRetweetedStatus().getUser().getId() == user.NumericId &&
                            checkOwn == null) {
                        showNotification(R.integer.notification_retweeted, preformedStatus, status.getUser());

                        //Put Response Stand-By
                        retweetResponseStandBy.put(preformedStatus.getUser().getId(), preformedStatus);
                    }
                    else if (!status.isRetweet() && !mute[MuteConfig.MUTE_NOTIF_MENTION]) {
                        UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                        for (UserMentionEntity ume : userMentionEntities) {
                            if (ume.getId() == user.NumericId) {
                                showNotification(R.integer.notification_replied, preformedStatus, status.getUser());
                            }
                        }
                    }

                    HashtagEntity[] hashtagEntities = status.getHashtagEntities();
                    for (HashtagEntity he : hashtagEntities) {
                        hashCache.put("#" + he.getText());
                    }

                    receivedStatuses.put(preformedStatus.getId(), preformedStatus);
                }

                @Override
                public void run() {
                    while (true) {
                        Pair<Stream, Status> p;
                        try {
                            p = queue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }

                        onStatus(p.first, p.second);
                    }
                }
            }
        }
    };
    public interface StatusListener {
        public String getStreamFilter();
        public void onStatus(AuthUserRecord from, PreformedStatus status, boolean muted);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
        public void onUpdatedStatus(AuthUserRecord from, int kind, Status status);
    }

    private class EventBuffer {
        public static final int E_STATUS = 0;
        public static final int E_DM     = 1;
        public static final int E_UPDATE = 2;

        private int eventType;
        private AuthUserRecord from;
        private Status status;
        private DirectMessage directMessage;
        private int kind;
        private boolean muted;

        public EventBuffer(AuthUserRecord from, PreformedStatus status, boolean muted) {
            this.eventType = E_STATUS;
            this.from = from;
            this.status = status;
            this.muted = muted;
        }

        public EventBuffer(AuthUserRecord from, DirectMessage directMessage) {
            this.eventType = E_DM;
            this.from = from;
            this.directMessage = directMessage;
        }

        public EventBuffer(AuthUserRecord from, int kind, Status status) {
            this.eventType = E_UPDATE;
            this.from = from;
            this.kind = kind;
            this.status = status;
        }

        public void sendBufferedEvent(StatusListener listener) {
            switch (eventType) {
                case E_STATUS:
                    listener.onStatus(from, (PreformedStatus) status, muted);
                    break;
                case E_DM:
                    listener.onDirectMessage(from, directMessage);
                    break;
                case E_UPDATE:
                    listener.onUpdatedStatus(from, kind, status);
                    break;
            }
        }
    }
    private List<StatusListener> statusListeners = new ArrayList<>();
    private Map<StatusListener, Queue<EventBuffer>> statusBuffer = new HashMap<>();

    private class UserUpdateDelayer {
        private Thread thread;
        private Queue<User> queue = new LinkedList<>();
        private final Object queueLock = new Object();
        private volatile boolean shutdown;

        private UserUpdateDelayer() {
            thread = new Thread(new Worker(), "UserUpdateDelayer");
            thread.start();
        }

        public void enqueue(User user) {
            synchronized (queueLock) {
                queue.offer(user);
            }
        }

        public void shutdown() {
            shutdown = true;
        }

        private class Worker implements Runnable {
            @Override
            public void run() {
                while (!shutdown) {
                    List<User> work;
                    synchronized (queueLock) {
                        work = new ArrayList<>(queue);
                        queue.clear();
                    }

                    if (!work.isEmpty()) {
                        getDatabase().beginTransaction();
                        try {
                            for (User user : work) {
                                getDatabase().updateRecord(new DBUser(user));
                            }
                            getDatabase().setTransactionSuccessful();
                        } finally {
                            getDatabase().endTransaction();
                        }
                    }

                    try {
                        int time = work.size() * 4 + 100;
                        Thread.sleep(time);
                        if (time >= 110) {
                            Log.d("UserUpdateDelayer", "Next update is " + time + "ms later");
                        }

                        while (getDatabase() == null) {
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException ignore) {}
                }
            }
        }
    }
    private UserUpdateDelayer userUpdateDelayer;

    public StatusManager(TwitterService service) {
        this.context = this.service = service;

        this.suppressor = service.getSuppressor();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.handler = new Handler();

        //システムサービスの取得
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(context);

        //遅延処理スレッドの開始
        userUpdateDelayer = new UserUpdateDelayer();
    }

    private CentralDatabase getDatabase() {
        return service.getDatabase();
    }

    public HashCache getHashCache() {
        return hashCache;
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        ArrayList<AuthUserRecord> activeUsers = new ArrayList<>();
        for (StreamUser su : streamUsers) {
            activeUsers.add(su.getUserRecord());
        }
        return activeUsers;
    }

    public int getStreamUsersCount() {
        return streamUsers.size();
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void start() {
        Log.d(LOG_TAG, "call start");

        for (StreamUser u : streamUsers) {
            u.start();
        }
        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().start();
        }

        isStarted = true;
    }

    public void stop() {
        Log.d(LOG_TAG, "call stop");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        isStarted = false;
    }

    public void startAsync() {
        new ParallelAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                start();
                return null;
            }
        }.executeParallel();
    }

    public void stopAsync() {
        new ParallelAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                stop();
                return null;
            }
        }.executeParallel();
    }

    public void shutdownAll() {
        Log.d(LOG_TAG, "call shutdownAll");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        streamUsers.clear();
        statusListeners.clear();
        statusBuffer.clear();

        receivedStatuses.clear();

        userUpdateDelayer.shutdown();
    }

    public void addStatusListener(StatusListener l) {
        if (statusListeners != null && !statusListeners.contains(l)) {
            statusListeners.add(l);
            Log.d("TwitterService", "Added StatusListener");
            if (statusBuffer.containsKey(l)) {
                Queue<EventBuffer> eventBuffers = statusBuffer.get(l);
                Log.d("TwitterService", "バッファ内に" + eventBuffers.size() + "件のツイートが保持されています.");
                while (!eventBuffers.isEmpty()) {
                    eventBuffers.poll().sendBufferedEvent(l);
                }
                statusBuffer.remove(l);
            }
        }
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners != null && statusListeners.contains(l)) {
            statusListeners.remove(l);
            Log.d("TwitterService", "Removed StatusListener");
            statusBuffer.put(l, new LinkedList<EventBuffer>());
        }
    }

    public void startUserStream(AuthUserRecord userRecord) {
        StreamUser su = new StreamUser(context, userRecord);
        su.setListener(listener);
        streamUsers.add(su);
        if (isStarted) {
            su.start();
        }
    }

    public void stopUserStream(AuthUserRecord userRecord) {
        StreamUser remove = null;
        for (StreamUser su : streamUsers) {
            if (su.getUserRecord().equals(userRecord)) {
                su.stop();
                remove = su;
                break;
            }
        }
        if (remove != null) {
            statusListeners.remove(remove);
            streamUsers.remove(remove);
        }
    }

    public void startFilterStream(String query, AuthUserRecord manager) {
        if (!filterMap.containsKey(query)) {
            FilterStream filterStream = new FilterStream(context, manager, query);
            filterStream.setListener(listener);
            filterMap.put(query, filterStream);
            if (isStarted) {
                filterStream.start();
                showToast("Start FilterStream:" + query);
            }
        }
    }

    public void stopFilterStream(String query) {
        if (filterMap.containsKey(query)) {
            FilterStream filterStream = filterMap.get(query);
            filterStream.stop();
            filterMap.remove(query);
            showToast("Stop FilterStream:" + query);
        }
    }

    public void reconnectAsync() {
        new SimpleAsyncTask() {
            @Override
            protected Void doInBackground(Void... params) {
                for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
                    e.getValue().stop();
                    e.getValue().start();
                }

                for (final StreamUser su : streamUsers) {
                    su.stop();
                    su.start();
                }
                return null;
            }
        }.execute();
    }

    private void showToast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static LongSparseArray<PreformedStatus> getReceivedStatuses() {
        return receivedStatuses;
    }

    public void sendFakeBroadcast(String methodName, Class[] argTypes, Object... args) {
        try {
            Class[] newArgTypes = new Class[argTypes.length+1];
            newArgTypes[0] = Stream.class;
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
            Method m = listener.getClass().getMethod(methodName, newArgTypes);
            Object[] newArgs = new Object[args.length+1];
            System.arraycopy(args, 0, newArgs, 1, args.length);
            for (StreamUser streamUser : streamUsers) {
                newArgs[0] = streamUser;
                m.invoke(listener, newArgs);
            }
            for (Map.Entry<String, FilterStream> entry : filterMap.entrySet()) {
                newArgs[0] = entry.getValue();
                m.invoke(listener, newArgs);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setAutoMuteConfigs(List<AutoMuteConfig> autoMuteConfigs) {
        this.autoMuteConfigs = autoMuteConfigs;
    }
}
