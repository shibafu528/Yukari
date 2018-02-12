package shibafu.yukari.twitter.statusmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import info.shibafu528.yukari.processor.autorelease.AutoRelease;
import info.shibafu528.yukari.processor.autorelease.AutoReleaser;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.linkage.TimelineHub;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.entity.TwitterMessage;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.entity.TwitterUser;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import shibafu.yukari.util.Releasable;
import shibafu.yukari.util.StringUtil;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager implements Releasable {
    private static LruCache<Long, PreformedStatus> receivedStatuses = new LruCache<>(512);

    private static final boolean PUT_STREAM_LOG = false;

    private static final String LOG_TAG = "StatusManager";

    @AutoRelease TwitterService service;
    @AutoRelease Suppressor suppressor;

    @AutoRelease Context context;
    @AutoRelease SharedPreferences sharedPreferences;
    private Handler handler;

    //通知マネージャ
    @AutoRelease StatusNotifier notifier;

    //ステータス
    private boolean isStarted;

    //Streaming
    private List<StreamUser> streamUsers = new ArrayList<>();
    private Map<AuthUserRecord, FilterStream> filterMap = new HashMap<>();
    private StreamListenerEx listener = new StreamListenerEx();

    private SyncReference<List<StatusListener>> statusListeners = new SyncReference<>(new ArrayList<>());
    private SyncReference<Map<String, Pair<StatusListener, Queue<EventBuffer>>>> statusBuffer = new SyncReference<>(new HashMap<>());

    private UserUpdateDelayer userUpdateDelayer;

    @AutoRelease TimelineHub hub;

    public StatusManager(TwitterService service) {
        this.context = this.service = service;

        this.suppressor = service.getSuppressor();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.handler = new Handler();

        this.hub = service.getTimelineHub();

        //通知マネージャの開始
        notifier = new StatusNotifier(service);

        //遅延処理スレッドの開始
        userUpdateDelayer = new UserUpdateDelayer(service.getDatabase());
    }

    @Override
    public void release() {
        streamUsers.clear();
        statusListeners.sync(List::clear);
        statusBuffer.sync(Map::clear);

        receivedStatuses.evictAll();

        notifier.release();
        userUpdateDelayer.shutdown();

        AutoReleaser.release(this);
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

    //<editor-fold desc="Connectivity">
    public boolean isStarted() {
        return isStarted;
    }

    public void start() {
        Log.d(LOG_TAG, "call start");

        for (StreamUser u : streamUsers) {
            u.start();
        }
        for (Map.Entry<AuthUserRecord, FilterStream> e : filterMap.entrySet()) {
            e.getValue().start();
        }

        isStarted = true;
    }

    public void stop() {
        Log.d(LOG_TAG, "call stop");

        for (Map.Entry<AuthUserRecord, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        isStarted = false;
    }

    public void startAsync() {
        ParallelAsyncTask.executeParallel(this::start);
    }

    public void stopAsync() {
        ParallelAsyncTask.executeParallel(this::stop);
    }

    public void shutdownAll() {
        Log.d(LOG_TAG, "call shutdownAll");

        for (Map.Entry<AuthUserRecord, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        release();
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
        for (Iterator<StreamUser> iterator = streamUsers.iterator(); iterator.hasNext(); ) {
            StreamUser su = iterator.next();
            if (su.getUserRecord().equals(userRecord)) {
                su.stop();
                iterator.remove();
                break;
            }
        }
    }

    public void startFilterStream(String query, AuthUserRecord manager) {
        FilterStream filterStream = FilterStream.getInstance(context, manager);
        filterStream.setListener(listener);
        filterStream.addQuery(query);
        filterMap.put(manager, filterStream);
        if (isStarted) {
            if (1 < filterStream.getQueryCount()) {
                // 再起動
                filterStream.stop();
            }
            filterStream.start();
            showToast("Start FilterStream:" + query);
        }
    }

    public void stopFilterStream(String query, AuthUserRecord manager) {
        if (filterMap.containsKey(manager)) {
            FilterStream filterStream = filterMap.get(manager);
            filterStream.stop();
            filterStream.removeQuery(query);
            if (0 < filterStream.getQueryCount()) {
                // 再起動
                filterStream.start();
            } else {
                filterMap.remove(manager);
            }
            showToast("Stop FilterStream:" + query);
        }
    }

    public void reconnectAsync() {
        SimpleAsyncTask.execute(() -> {
            for (Map.Entry<AuthUserRecord, FilterStream> e : filterMap.entrySet()) {
                e.getValue().stop();
                e.getValue().start();
            }

            for (final StreamUser su : streamUsers) {
                su.stop();
                su.start();
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Subscribe">
    public void addStatusListener(final StatusListener l) {
        if (statusListeners != null && !statusListeners.syncReturn(s -> s.contains(l))){
            statusListeners.sync(s -> s.add(l));
            Log.d("TwitterService", "Added StatusListener : " + l.getSubscribeIdentifier());
            if (statusBuffer.syncReturn(s -> s.containsKey(l.getSubscribeIdentifier()))) {
                Queue<EventBuffer> eventBuffers = statusBuffer.syncReturn(s -> s.get(l.getSubscribeIdentifier())).second;
                statusBuffer.sync(s -> s.remove(l.getSubscribeIdentifier()));
                Log.d("TwitterService", "SubID:" + l.getSubscribeIdentifier() + " -> バッファ内に" + eventBuffers.size() + "件のツイートが保持されています.");
                while (!eventBuffers.isEmpty()) {
                    eventBuffers.poll().sendBufferedEvent(l);
                }
            }
        }
    }

    public void removeStatusListener(final StatusListener l) {
        if (statusListeners != null && statusListeners.syncReturn(s -> s.contains(l))) {
            statusListeners.sync(s -> s.remove(l));
            Log.d("TwitterService", "Removed StatusListener : " + l.getSubscribeIdentifier());
            statusBuffer.sync(s -> s.put(l.getSubscribeIdentifier(), Pair.create(l, new LinkedBlockingQueue<>())));
        }
    }
    //</editor-fold>

    private void showToast(final String text) {
        handler.post(() -> Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public static LruCache<Long, PreformedStatus> getReceivedStatuses() {
        return receivedStatuses;
    }

    public void loadQuotedEntities(PreformedStatus preformedStatus) {
        for (Long id : preformedStatus.getQuoteEntities()) {
            if (receivedStatuses.get(id) == null) {
                AuthUserRecord userRecord = preformedStatus.getRepresentUser();

                new TwitterAsyncTask<Void>(context) {

                    @Override
                    protected TwitterException doInBackground(@NotNull Void... params) {
                        Twitter twitter = service.getTwitter(userRecord);
                        if (twitter != null) {
                            try {
                                twitter4j.Status s = twitter.showStatus(id);
                                receivedStatuses.put(s.getId(), new PreformedStatus(s, userRecord));
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return e;
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(TwitterException e) {
                        if (hub != null) {
                            hub.onForceUpdateUI();
                        }
                    }
                }.executeParallel();
            }
        }
    }

    /**
     * 参照をラップし、同期操作を行う機能を提供します。
     * @param <T> 参照の型
     */
    private static class SyncReference<T> {
        private final Object mutex = this;
        private T reference;

        public SyncReference(T reference) {
            this.reference = reference;
        }

        /**
         * ラップされている参照を取得します。<b>このメソッドは同期化されません。</b>
         * @return ラップされている参照
         */
        public T getReference() {
            return reference;
        }

        /**
         * ラップされている参照に対して、関数型インターフェースを適用します。
         * @param lambda 適用する関数
         */
        public void sync(Consumer<T> lambda) {
            synchronized (mutex) {
                lambda.accept(reference);
            }
        }

        /**
         * ラップされている参照に対して関数型インターフェースを適用し、結果を取得します。
         * @param lambda 適用する関数
         * @param <Result> 結果の型
         * @return 関数の返り値
         */
        public <Result> Result syncReturn(Function<T, Result> lambda) {
            synchronized (mutex) {
                return lambda.apply(reference);
            }
        }
    }

    private interface Consumer<T> {
        void accept(T value);
    }

    private interface Function<T, R> {
        R apply(T value);
    }

    private class StreamListenerEx implements StreamListener {
        @Override
        public void onFavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onFavorite", String.format("f:%s s:%d", from.getUserRecord().ScreenName, status.getId()));

            PreformedStatus preformedStatus = new PreformedStatus(status, from.getUserRecord());
            TwitterStatus twitterStatus = new TwitterStatus(preformedStatus, from.getUserRecord());
            TwitterUser twitterUser = new TwitterUser(user);

            hub.onFavorite(twitterUser, twitterStatus);
        }

        @Override
        public void onUnfavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onUnfavorite", String.format("f:%s s:%s", from.getUserRecord().ScreenName, status.getText()));

            PreformedStatus preformedStatus = new PreformedStatus(status, from.getUserRecord());
            TwitterStatus twitterStatus = new TwitterStatus(preformedStatus, from.getUserRecord());
            TwitterUser twitterUser = new TwitterUser(user);

            hub.onUnfavorite(twitterUser, twitterStatus);
        }

        @Override
        public void onFollow(Stream from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(Stream from, DirectMessage directMessage) {
            hub.onDirectMessage("Twitter.StreamManager", new TwitterMessage(directMessage, from.getUserRecord()), true);
        }

        @Override
        public void onBlock(Stream from, User user, User user2) {

        }

        @Override
        public void onUnblock(Stream from, User user, User user2) {

        }

        @Override
        public void onStatus(Stream from, Status status) {
            statusQueue.enqueue(from, status);
        }

        @Override
        public void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice) {
            hub.onDelete(TwitterStatus.class, statusDeletionNotice.getStatusId());
        }

        @Override
        public void onDeletionNotice(Stream from, long directMessageId, long userId) {
            hub.onDelete(TwitterMessage.class, directMessageId);
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
                            String.format("onStatus(Registered Listener %d): %s from %s [%s:%s]:@%s %s",
                                    statusListeners.syncReturn(List::size),
                                    StringUtil.formatDate(status.getCreatedAt()),
                                    from.getUserRecord().ScreenName,
                                    from.getClass().getSimpleName(),
                                    status.getClass().getSimpleName(),
                                    status.getUser().getScreenName(),
                                    status.getText()));

                    hub.onStatus("Twitter.StreamManager", new TwitterStatus(new PreformedStatus(status, from.getUserRecord()), from.getUserRecord()), true);
                }

                @SuppressWarnings("InfiniteLoopStatement")
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
    }

}
