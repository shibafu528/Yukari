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
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.TabType;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager {
    private static final String LOG_TAG = "StatusManager";
    private static final int NOTIF_FAVED = 1;
    private static final int NOTIF_REPLY = 2;
    private static final int NOTIF_RETWEET = 3;

    //バイブレーションパターン
    private final static long[] VIB_REPLY = {450, 130, 140, 150};
    private final static long[] VIB_RETWEET = {150, 130, 300, 150};
    private final static long[] VIB_FAVED = {140, 100};

    private TwitterService service;

    private Context context;
    private NotificationManager notificationManager;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private SharedPreferences sharedPreferences;
    private Handler handler;

    //キャッシュ
    private HashCache hashCache;

    //ステータス
    private boolean isStarted;

    //Streaming
    private List<StreamUser> streamUsers = new ArrayList<StreamUser>();
    private Map<String, FilterStream> filterMap = new HashMap<String, FilterStream>();
    private StreamListener listener = new StreamListener() {

        @Override
        public void onFavorite(Stream from, User user, User user2, Status status) {
            getDatabase().updateUser(new DBUser(status.getUser()));
            getDatabase().updateUser(new DBUser(user));
            getDatabase().updateUser(new DBUser(user2));
            if (from.getUserRecord().NumericId == user.getId())
                return;

            showNotification(NOTIF_FAVED, status, user);
        }

        @Override
        public void onUnfavorite(Stream from, User user, User user2, Status status) {

        }

        @Override
        public void onFollow(Stream from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(Stream from, DirectMessage directMessage) {
            getDatabase().updateUser(new DBUser(directMessage.getSender()));
            for (StatusListener sl : statusListeners) {
                sl.onDirectMessage(from.getUserRecord(), directMessage);
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), directMessage));
            }
        }

        @Override
        public void onBlock(Stream from, User user, User user2) {

        }

        @Override
        public void onUnblock(Stream from, User user, User user2) {

        }

        @Override
        public void onStatus(Stream from, Status status) {
            if (from == null || status == null || status.getUser() == null) {
                Log.w(LOG_TAG, "onStatus, NullPointer!");
                return;
            }
            Log.d(LOG_TAG, "onStatus(Registered Listener " + statusListeners.size() + "): @" + status.getUser().getScreenName() + " " + status.getText());
            getDatabase().updateUser(new DBUser(status.getUser()));
            AuthUserRecord user = from.getUserRecord();

            //自分のツイートかどうかマッチングを行う
            AuthUserRecord checkOwn = service.isMyTweet(status);
            if (checkOwn != null) {
                //自分のツイートであれば受信元は発信元アカウントということにする
                user = checkOwn;
            }

            PreformedStatus preformedStatus = new PreformedStatus(status, user);
            for (StatusListener sl : statusListeners) {
                if (deliver(sl, from)) {
                    sl.onStatus(user, preformedStatus);
                }
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                if (deliver(e.getKey(), from)) {
                    e.getValue().offer(new EventBuffer(user, preformedStatus));
                }
            }

            if (status.isRetweet() && status.getRetweetedStatus().getUser().getId() == user.NumericId &&
                    checkOwn == null) {
                showNotification(NOTIF_RETWEET, preformedStatus, status.getUser());
            }
            else if (!status.isRetweet()) {
                UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                for (UserMentionEntity ume : userMentionEntities) {
                    if (ume.getId() == user.NumericId) {
                        showNotification(NOTIF_REPLY, preformedStatus, status.getUser());
                    }
                }
            }

            HashtagEntity[] hashtagEntities = status.getHashtagEntities();
            for (HashtagEntity he : hashtagEntities) {
                hashCache.put("#" + he.getText());
            }
        }

        @Override
        public void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice) {
            for (StatusListener sl : statusListeners) {
                sl.onDelete(from.getUserRecord(), statusDeletionNotice);
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new EventBuffer(from.getUserRecord(), statusDeletionNotice));
            }
        }

        private void showNotification(int category, Status status, User actionBy) {
            int prefValue = 5;
            switch (category) {
                case NOTIF_REPLY:
                    prefValue = sharedPreferences.getInt("pref_notif_mention", 5);
                    break;
                case NOTIF_RETWEET:
                    prefValue = sharedPreferences.getInt("pref_notif_rt", 5);
                    break;
                case NOTIF_FAVED:
                    prefValue = sharedPreferences.getInt("pref_notif_fav", 5);
                    break;
            }
            NotificationType notificationType = new NotificationType(prefValue);

            if (notificationType.isEnabled()) {
                int icon = 0;
                Uri sound = null;
                String titleHeader = "", tickerHeader = "";
                long[] pattern = null;
                switch (category) {
                    case NOTIF_REPLY:
                        icon = R.drawable.ic_stat_reply;
                        titleHeader = "Reply from @";
                        tickerHeader = "リプライ : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
                        pattern = VIB_REPLY;
                        break;
                    case NOTIF_RETWEET:
                        icon = R.drawable.ic_stat_retweet;
                        titleHeader = "Retweeted by @";
                        tickerHeader = "RTされました : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_rt");
                        pattern = VIB_RETWEET;
                        break;
                    case NOTIF_FAVED:
                        icon = R.drawable.ic_stat_favorite;
                        titleHeader = "Faved by @";
                        tickerHeader = "ふぁぼられ : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_fav");
                        pattern = VIB_FAVED;
                        break;
                }
                if (notificationType.getNotificationType() == NotificationType.TYPE_NOTIF) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());
                    builder.setSmallIcon(icon);
                    builder.setContentTitle(titleHeader + actionBy.getScreenName());
                    builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                    builder.setTicker(tickerHeader + actionBy.getScreenName());
                    if (notificationType.isUseSound()) {
                        builder.setSound(sound, AudioManager.STREAM_NOTIFICATION);
                    }
                    if (notificationType.isUseVibration()) {
                        vibrate(pattern, -1);
                    }
                    builder.setAutoCancel(true);
                    switch (category) {
                        case NOTIF_REPLY:
                        {
                            Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                    context.getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
                            builder.setContentIntent(pendingIntent);
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
                            status.getUser().getScreenName() + ": " + status.getText();
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
    };
    public interface StatusListener {
        public String getStreamFilter();
        public void onStatus(AuthUserRecord from, PreformedStatus status);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
        public void onDelete(AuthUserRecord from, StatusDeletionNotice statusDeletionNotice);
    }
    private class EventBuffer {
        public static final int E_STATUS = 0;
        public static final int E_DM     = 1;
        public static final int E_DELETE = 2;

        private int eventType;
        private AuthUserRecord from;
        private PreformedStatus status;
        private DirectMessage directMessage;
        private StatusDeletionNotice statusDeletionNotice;

        public EventBuffer(AuthUserRecord from, PreformedStatus status) {
            this.eventType = E_STATUS;
            this.from = from;
            this.status = status;
        }

        public EventBuffer(AuthUserRecord from, DirectMessage directMessage) {
            this.eventType = E_DM;
            this.from = from;
            this.directMessage = directMessage;
        }

        public EventBuffer(AuthUserRecord from, StatusDeletionNotice statusDeletionNotice) {
            this.eventType = E_DELETE;
            this.from = from;
            this.statusDeletionNotice = statusDeletionNotice;
        }

        public void sendBufferedEvent(StatusListener listener) {
            switch (eventType) {
                case E_STATUS:
                    listener.onStatus(from, status);
                    break;
                case E_DM:
                    listener.onDirectMessage(from, directMessage);
                    break;
                case E_DELETE:
                    listener.onDelete(from, statusDeletionNotice);
                    break;
            }
        }
    }
    private List<StatusListener> statusListeners = new ArrayList<StatusListener>();
    private Map<StatusListener, Queue<EventBuffer>> statusBuffer = new HashMap<StatusListener, Queue<EventBuffer>>();

    public StatusManager(TwitterService service) {
        this.context = this.service = service;

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.handler = new Handler();

        //システムサービスの取得
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(context);
    }

    private CentralDatabase getDatabase() {
        return service.getDatabase();
    }

    public HashCache getHashCache() {
        return hashCache;
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        ArrayList<AuthUserRecord> activeUsers = new ArrayList<AuthUserRecord>();
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

    public void shutdownAll() {
        Log.d(LOG_TAG, "call shutdownAll");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }

        for (final StreamUser su : streamUsers) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    su.stop();
                }
            });
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        streamUsers.clear();
        streamUsers = null;
        statusListeners.clear();
        statusListeners = null;
        listener = null;
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
}
