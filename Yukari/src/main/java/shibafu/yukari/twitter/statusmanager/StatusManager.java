package shibafu.yukari.twitter.statusmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.*;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import shibafu.yukari.util.StringUtil;
import twitter4j.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    public static final int UPDATE_NOTIFY = 5;
    public static final int UPDATE_FORCE_UPDATE_UI = 6;
    public static final int UPDATE_WIPE_TWEETS = 0xff;

    private static final String LOG_TAG = "StatusManager";

    private TwitterService service;
    private Suppressor suppressor;
    private List<AutoMuteConfig> autoMuteConfigs;

    private Context context;
    private SharedPreferences sharedPreferences;
    private Handler handler;

    //通知マネージャ
    private StatusNotifier notifier;

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
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_FAVED, new FavFakeStatus(status.getId(), true, user)));
            }

            userUpdateDelayer.enqueue(status.getUser());
            userUpdateDelayer.enqueue(user);
            userUpdateDelayer.enqueue(user2);

            if (from.getUserRecord().NumericId == user.getId()) return;

            PreformedStatus preformedStatus = new PreformedStatus(status, from.getUserRecord());
            boolean[] mute = suppressor.decision(preformedStatus);
            boolean[] muteUser = suppressor.decisionUser(user);
            if (!(mute[MuteConfig.MUTE_NOTIF_FAV] || muteUser[MuteConfig.MUTE_NOTIF_FAV])) {
                notifier.showNotification(R.integer.notification_faved, preformedStatus, user);
                createHistory(from, HistoryStatus.KIND_FAVED, user, preformedStatus);
            }
        }

        @Override
        public void onUnfavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onUnfavorite", String.format("f:%s s:%s", from.getUserRecord().ScreenName, status.getText()));
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user)));
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
                e.getValue().offer(new MessageEventBuffer(from.getUserRecord(), directMessage));
            }

            boolean checkOwn = service.isMyTweet(directMessage) != null;
            if (!checkOwn) {
                notifier.showNotification(R.integer.notification_message, directMessage, directMessage.getSender());
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
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED, new FakeStatus(statusDeletionNotice.getStatusId())));
            }
        }

        public void onWipe(Stream from) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0)));
            }
        }

        public void onForceUpdateUI(Stream from) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_FORCE_UPDATE_UI, new FakeStatus(0));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_FORCE_UPDATE_UI, new FakeStatus(0)));
            }
        }

        @Override
        public void onDeletionNotice(Stream from, long directMessageId, long userId) {
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId));
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId)));
            }
        }

        private void createHistory(Stream from, int kind, User eventBy, Status status) {
            HistoryStatus historyStatus = new HistoryStatus(System.currentTimeMillis(), kind, eventBy, status);
            EventBuffer eventBuffer = new UpdateEventBuffer(from.getUserRecord(), UPDATE_NOTIFY, historyStatus);
            for (StatusListener sl : statusListeners) {
                sl.onUpdatedStatus(from.getUserRecord(), UPDATE_NOTIFY, historyStatus);
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(eventBuffer);
            }
            updateBuffer.add(eventBuffer);
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
                        notifier.showNotification(R.integer.notification_respond, deliverStatus, deliverStatus.getUser());
                    }
                    for (StatusListener sl : statusListeners) {
                        if (deliver(sl, from)) {
                            sl.onStatus(user, deliverStatus, muted);
                        }
                    }
                    deliverStatus = (deliverStatus instanceof MetaStatus)? new MetaStatus(preformedStatus, "queued") : deliverStatus;
                    for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                        if (deliver(e.getKey(), from)) {
                            e.getValue().offer(new StatusEventBuffer(user, deliverStatus, muted));
                        }
                    }

                    if (status.isRetweet() &&
                            !mute[MuteConfig.MUTE_NOTIF_RT] &&
                            status.getRetweetedStatus().getUser().getId() == user.NumericId &&
                            checkOwn == null) {
                        notifier.showNotification(R.integer.notification_retweeted, preformedStatus, status.getUser());
                        createHistory(from, HistoryStatus.KIND_RETWEETED, status.getUser(), preformedStatus.getRetweetedStatus());

                        //Put Response Stand-By
                        retweetResponseStandBy.put(preformedStatus.getUser().getId(), preformedStatus);
                    }
                    else if (!status.isRetweet() && !mute[MuteConfig.MUTE_NOTIF_MENTION]) {
                        UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                        for (UserMentionEntity ume : userMentionEntities) {
                            if (ume.getId() == user.NumericId) {
                                notifier.showNotification(R.integer.notification_replied, preformedStatus, status.getUser());
                            }
                        }
                    }

                    HashtagEntity[] hashtagEntities = status.getHashtagEntities();
                    for (HashtagEntity he : hashtagEntities) {
                        hashCache.put("#" + he.getText());
                    }

                    receivedStatuses.put(preformedStatus.getId(), preformedStatus);
                    loadQuotedEntities(preformedStatus);
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
        String getStreamFilter();
        void onStatus(AuthUserRecord from, PreformedStatus status, boolean muted);
        void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
        void onUpdatedStatus(AuthUserRecord from, int kind, Status status);
    }

    private List<StatusListener> statusListeners = new ArrayList<>();
    private Map<StatusListener, Queue<EventBuffer>> statusBuffer = new HashMap<>();
    private List<EventBuffer> updateBuffer = new ArrayList<>();

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

        //通知マネージャの開始
        notifier = new StatusNotifier(context, this);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(context);

        //遅延処理スレッドの開始
        userUpdateDelayer = new UserUpdateDelayer();
    }

    private CentralDatabase getDatabase() {
        return service == null ? null : service.getDatabase();
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

    AuthUserRecord findUserRecord(User user) {
        for (AuthUserRecord userRecord : service.getUsers()) {
            if (userRecord.NumericId == user.getId()) {
                return userRecord;
            }
        }
        return null;
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

        notifier.release();
        notifier = null;

        userUpdateDelayer.shutdown();

        this.suppressor = null;
        this.sharedPreferences = null;
        this.service = null;
        this.context = null;
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
            } else {
                for (EventBuffer eventBuffer : updateBuffer) {
                    eventBuffer.sendBufferedEvent(l);
                }
            }
        }
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners != null && statusListeners.contains(l)) {
            statusListeners.remove(l);
            Log.d("TwitterService", "Removed StatusListener");
            statusBuffer.put(l, new LinkedList<>());
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
        handler.post(() -> Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public static LongSparseArray<PreformedStatus> getReceivedStatuses() {
        return receivedStatuses;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
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
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setAutoMuteConfigs(List<AutoMuteConfig> autoMuteConfigs) {
        this.autoMuteConfigs = autoMuteConfigs;
    }

    public void loadQuotedEntities(PreformedStatus preformedStatus) {
        for (Long id : preformedStatus.getQuoteEntities()) {
            if (receivedStatuses.indexOfKey(id) < 0) {
                new TwitterAsyncTask<Object>(context) {

                    @Override
                    protected TwitterException doInBackground(Object... params) {
                        Twitter twitter = service.getTwitter();
                        AuthUserRecord userRecord = (AuthUserRecord) params[1];
                        twitter.setOAuthAccessToken(userRecord.getAccessToken());
                        try {
                            twitter4j.Status s = twitter.showStatus(((long) params[0]));
                            receivedStatuses.put(s.getId(), new PreformedStatus(s, userRecord));
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return e;
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(TwitterException e) {
                        super.onPostExecute(e);
                        sendFakeBroadcast("onForceUpdateUI", new Class[]{});
                    }
                }.executeParallel(id, preformedStatus.getRepresentUser());
            }
        }
    }
}
