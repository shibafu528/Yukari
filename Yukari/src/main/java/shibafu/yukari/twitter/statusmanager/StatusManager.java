package shibafu.yukari.twitter.statusmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import info.shibafu528.yukari.exvoice.MRuby;
import info.shibafu528.yukari.exvoice.converter.StatusConverter;
import info.shibafu528.yukari.exvoice.model.Message;
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin;
import info.shibafu528.yukari.processor.autorelease.AutoRelease;
import info.shibafu528.yukari.processor.autorelease.AutoReleaser;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.R;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.DirectMessageCompat;
import shibafu.yukari.twitter.statusimpl.FakeStatus;
import shibafu.yukari.twitter.statusimpl.FavFakeStatus;
import shibafu.yukari.twitter.statusimpl.HistoryStatus;
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus;
import shibafu.yukari.twitter.statusimpl.MetaStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusimpl.RespondNotifyStatus;
import shibafu.yukari.twitter.statusimpl.RestCompletedStatus;
import shibafu.yukari.twitter.streaming.AutoReloadStream;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.streaming.RestStream;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import shibafu.yukari.util.Releasable;
import shibafu.yukari.util.StringUtil;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager implements Releasable {
    private static LruCache<Long, PreformedStatus> receivedStatuses = new LruCache<>(512);

    private static final boolean PUT_STREAM_LOG = false;
    private static final long RESPONSE_STAND_BY_EXPIRES = 10 * 60 * 1000;

    public static final int UPDATE_FAVED = 1;
    public static final int UPDATE_UNFAVED = 2;
    public static final int UPDATE_DELETED = 3;
    public static final int UPDATE_DELETED_DM = 4;
    public static final int UPDATE_NOTIFY = 5;
    public static final int UPDATE_FORCE_UPDATE_UI = 6;
    public static final int UPDATE_REST_COMPLETED = 7;
    public static final int UPDATE_WIPE_TWEETS = 0xff;

    private static final String LOG_TAG = "StatusManager";

    @AutoRelease TwitterService service;
    @AutoRelease Suppressor suppressor;
    private List<AutoMuteConfig> autoMuteConfigs;
    private LongSparseArray<Pattern> autoMutePatternCache = new LongSparseArray<>();

    @AutoRelease Context context;
    @AutoRelease SharedPreferences sharedPreferences;
    private Handler handler;

    //通知マネージャ
    @AutoRelease StatusNotifier notifier;

    //RT-Response Listen (Key:RTed User ID, Value:Response StandBy Status)
    private LongSparseArray<Pair<PreformedStatus, Long>> retweetResponseStandBy = new LongSparseArray<>();

    //キャッシュ
    private HashCache hashCache;

    //ステータス
    private boolean isStarted;

    //実行中の非同期REST
    private LongSparseArray<ParallelAsyncTask<Void, Void, Void>> workingRestQueries = new LongSparseArray<>();

    //Streaming
    private List<Stream> streamUsers = new ArrayList<>();
    private Map<AuthUserRecord, FilterStream> filterMap = new HashMap<>();
    private StreamListenerEx listener = new StreamListenerEx();

    private SyncReference<List<StatusListener>> statusListeners = new SyncReference<>(new ArrayList<>());
    private SyncReference<Map<String, Pair<StatusListener, Queue<EventBuffer>>>> statusBuffer = new SyncReference<>(new HashMap<>());
    private SyncReference<List<EventBuffer>> updateBuffer = new SyncReference<>(new ArrayList<>());

    private UserUpdateDelayer userUpdateDelayer;

    public StatusManager(TwitterService service) {
        this.context = this.service = service;

        this.suppressor = service.getSuppressor();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.handler = new Handler();

        //通知マネージャの開始
        notifier = new StatusNotifier(service);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(context);

        //遅延処理スレッドの開始
        userUpdateDelayer = new UserUpdateDelayer(service.getDatabase());
    }

    @Override
    public void release() {
        streamUsers.clear();
        statusListeners.sync(List::clear);
        statusBuffer.sync(Map::clear);

        int workingCount = workingRestQueries.size();
        for (int i = 0; i < workingCount; i++) {
            long key = workingRestQueries.keyAt(i);
            ParallelAsyncTask<Void, Void, Void> task = workingRestQueries.get(key);

            if (task != null && !task.isCancelled()) {
                task.cancel(true);
            }
        }

        receivedStatuses.evictAll();

        notifier.release();
        userUpdateDelayer.shutdown();

        AutoReleaser.release(this);
    }

    private CentralDatabase getDatabase() {
        return service == null ? null : service.getDatabase();
    }

    public HashCache getHashCache() {
        return hashCache;
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        ArrayList<AuthUserRecord> activeUsers = new ArrayList<>();
        for (Stream su : streamUsers) {
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

        for (Stream u : streamUsers) {
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
        for (Stream su : streamUsers) {
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
        for (Stream su : streamUsers) {
            su.stop();
        }

        release();
    }

    public void startUserStream(AuthUserRecord userRecord) {
        Stream su;
        if (sharedPreferences.getBoolean("pref_replace_auto_reload_stream", false)) {
            su = new AutoReloadStream(context, userRecord);
        } else {
            // R.I.P. UserStream (2018/08/24 0:00 JST)
            //su = new StreamUser(context, userRecord);
            return;
        }
        su.setListener(listener);
        streamUsers.add(su);
        if (isStarted) {
            su.start();
        }
    }

    public void stopUserStream(AuthUserRecord userRecord) {
        for (Iterator<Stream> iterator = streamUsers.iterator(); iterator.hasNext(); ) {
            Stream su = iterator.next();
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

            for (final Stream su : streamUsers) {
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
            } else {
                Integer size = updateBuffer.syncReturn(List::size);
                Log.d("TwitterService", String.format("ヒストリUIと接続されました. %d件のイベントがバッファ内に保持されています.", size));
                updateBuffer.sync(u -> {
                    for (EventBuffer eventBuffer : u) {
                        eventBuffer.sendBufferedEvent(l);
                    }
                });
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

    public void onWipe() {
        if (listener != null) {
            for (Stream streamUser : streamUsers) {
                listener.onWipe(streamUser);
            }
            for (Map.Entry<AuthUserRecord, FilterStream> entry : filterMap.entrySet()) {
                listener.onWipe(entry.getValue());
            }
        }
    }

    public void setAutoMuteConfigs(List<AutoMuteConfig> autoMuteConfigs) {
        this.autoMuteConfigs = autoMuteConfigs;
        autoMutePatternCache.clear();
        for (AutoMuteConfig autoMuteConfig : autoMuteConfigs) {
            if (autoMuteConfig.getMatch() == AutoMuteConfig.MATCH_REGEX) {
                try {
                    autoMutePatternCache.put(autoMuteConfig.getId(), Pattern.compile(autoMuteConfig.getQuery()));
                } catch (PatternSyntaxException e) {
                    autoMutePatternCache.put(autoMuteConfig.getId(), null);
                }
            }
        }
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
                        for (Stream streamUser : streamUsers) {
                            listener.onForceUpdateUI(streamUser);
                        }
                        for (Map.Entry<AuthUserRecord, FilterStream> entry : filterMap.entrySet()) {
                            listener.onForceUpdateUI(entry.getValue());
                        }
                    }
                }.executeParallel();
            }
        }
    }

    /**
     * 非同期RESTリクエストを開始します。結果はストリーミングと同一のインターフェースで配信されます。
     * @param restTag 通信結果の配信用タグ
     * @param userRecord 使用するアカウント
     * @param query RESTリクエストクエリ
     * @param pagingMaxId {@link Paging#maxId} に設定する値、負数の場合は設定しない
     * @param appendLoadMarker ページングのマーカーとして {@link LoadMarkerStatus} も配信するかどうか
     * @param loadMarkerTag ページングのマーカーに、どのクエリの続きを表しているのか識別するために付与するタグ
     * @return 開始された非同期処理に割り振ったキー。状態確認に使用できます。
     */
    public long requestRestQuery(@NonNull final String restTag,
                                 @NonNull final AuthUserRecord userRecord,
                                 @NonNull final RestQuery query,
                                 final long pagingMaxId,
                                 final boolean appendLoadMarker,
                                 final String loadMarkerTag) {
        final boolean isNarrowMode = sharedPreferences.getBoolean("pref_narrow", false);
        final long taskKey = System.currentTimeMillis();
        ParallelAsyncTask<Void, Void, Void> task = new ParallelAsyncTask<Void, Void, Void>() {
            private TwitterException exception;

            @Override
            protected Void doInBackground(Void... params) {
                Log.d("StatusManager", String.format("Begin AsyncREST: @%s - %s -> %s", userRecord.ScreenName, restTag, query.getClass().getName()));

                Stream stream = new RestStream(context, userRecord, restTag);
                Twitter twitter = service.getTwitter(userRecord);
                if (twitter == null) {
                    return null;
                }

                try {
                    Paging paging = new Paging();
                    if (!isNarrowMode) paging.setCount(100);
                    if (-1 < pagingMaxId) paging.setMaxId(pagingMaxId);

                    List<twitter4j.Status> responseList = query.getRestResponses(twitter, paging);
                    if (responseList == null) {
                        responseList = new ArrayList<>();
                    }
                    if (appendLoadMarker) {
                        LoadMarkerStatus markerStatus;

                        if (responseList.isEmpty()) {
                            markerStatus = new LoadMarkerStatus(paging.getMaxId(), paging.getMaxId(), userRecord.NumericId, loadMarkerTag);
                        } else {
                            twitter4j.Status last = responseList.get(responseList.size() - 1);
                            markerStatus = new LoadMarkerStatus(last.getId() - 1, last.getId(), userRecord.NumericId, loadMarkerTag);
                        }

                        responseList.add(0, markerStatus);
                    }

                    if (isCancelled()) return null;

                    // StreamManagerに流す
                    for (twitter4j.Status status : responseList) {
                        listener.onStatus(stream, status);
                    }

                    Log.d("StatusManager", String.format("Received REST: @%s - %s - %d statuses", userRecord.ScreenName, restTag, responseList.size()));
                } catch (TwitterException e) {
                    e.printStackTrace();
                    exception = e;
                } finally {
                    listener.onRestCompleted(stream, taskKey);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                workingRestQueries.remove(taskKey);
                if (exception != null) {
                    switch (exception.getStatusCode()) {
                        case 429:
                            Toast.makeText(context,
                                    String.format("[@%s]\nレートリミット超過\n次回リセット: %d分%d秒後\n時間を空けて再度操作してください",
                                            userRecord.ScreenName,
                                            exception.getRateLimitStatus().getSecondsUntilReset() / 60,
                                            exception.getRateLimitStatus().getSecondsUntilReset() % 60),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            String template;
                            if (exception.isCausedByNetworkIssue()) {
                                template = "[@%s]\n通信エラー: %d:%d\n%s";
                            } else {
                                template = "[@%s]\nエラー: %d:%d\n%s";
                            }
                            Toast.makeText(context,
                                    String.format(template,
                                            userRecord.ScreenName,
                                            exception.getStatusCode(),
                                            exception.getErrorCode(),
                                            exception.getErrorMessage()),
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }
        };
        task.executeParallel();
        workingRestQueries.put(taskKey, task);
        Log.d("StatusManager", String.format("Requested REST: @%s - %s", userRecord.ScreenName, restTag));

        return taskKey;
    }

    /**
     * 非同期RESTリクエストの実行状態を取得します。
     * @param taskKey {@link #requestRestQuery(String, AuthUserRecord, RestQuery, long, boolean, String)} の返り値
     * @return 実行中かつ中断されていなければ true
     */
    public boolean isWorkingRestQuery(long taskKey) {
        return workingRestQueries.get(taskKey) != null && !workingRestQueries.get(taskKey).isCancelled();
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
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_FAVED, new FavFakeStatus(status.getId(), true, user)));

            userUpdateDelayer.enqueue(status.getUser(), user, user2);

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
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user)));
        }

        @Override
        public void onFollow(Stream from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(Stream from, DirectMessage directMessage) {
            Twitter twitter = service.getTwitter(from.getUserRecord());
            if (twitter == null) return;

            try {
                ResponseList<User> users = twitter.lookupUsers(directMessage.getRecipientId(), directMessage.getSenderId());
                User sender = null;
                User recipient = null;
                for (User user : users) {
                    if (user.getId() == directMessage.getSenderId()) {
                        sender = user;
                    }
                    if (user.getId() == directMessage.getRecipientId()) {
                        recipient = user;
                    }
                }
                if (sender != null && recipient != null) {
                    DirectMessageCompat messageCompat = new DirectMessageCompat(directMessage, sender, recipient);

                    userUpdateDelayer.enqueue(messageCompat.getSender());
                    pushEventQueue(from, new MessageEventBuffer(from.getUserRecord(), messageCompat));

                    boolean checkOwn = service.isMyTweet(messageCompat) != null;
                    if (!checkOwn) {
                        notifier.showNotification(R.integer.notification_message, messageCompat, messageCompat.getSender());
                    }
                }
            } catch (TwitterException e) {
                e.printStackTrace();
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
            statusQueue.enqueue(from, status);
        }

        @Override
        public void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice) {
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED, new FakeStatus(statusDeletionNotice.getStatusId())));
        }

        @SuppressWarnings("unused")
        public void onWipe(Stream from) {
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0)));
        }

        @SuppressWarnings("unused")
        public void onForceUpdateUI(Stream from) {
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_FORCE_UPDATE_UI, new FakeStatus(0)));
        }

        @SuppressWarnings("unused")
        public void onRestCompleted(Stream from, Long taskKey) {
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_REST_COMPLETED, new RestCompletedStatus(((RestStream) from).getTag(), taskKey)));
        }

        @Override
        public void onDeletionNotice(Stream from, long directMessageId, long userId) {
            pushEventQueue(from, new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId)));
        }

        private void createHistory(Stream from, int kind, User eventBy, Status status) {
            HistoryStatus historyStatus = new HistoryStatus(System.currentTimeMillis(), kind, eventBy, status);
            EventBuffer eventBuffer = new UpdateEventBuffer(from.getUserRecord(), UPDATE_NOTIFY, historyStatus);
            pushEventQueue(from, eventBuffer);
            updateBuffer.sync(u -> u.add(eventBuffer));
        }

        private void pushEventQueue(Stream from, EventBuffer event) {
            pushEventQueue(from, event, true);
        }

        private void pushEventQueue(Stream from, EventBuffer event, boolean isBroadcast) {
            statusListeners.sync(sls -> {
                for (StatusListener sl : sls) {
                    if (isBroadcast || deliver(sl, from)) {
                        event.sendBufferedEvent(sl);
                    }
                }
            });
            statusBuffer.sync(sb -> {
                for (Map.Entry<String, Pair<StatusListener, Queue<EventBuffer>>> e : sb.entrySet()) {
                    if (isBroadcast || deliver(e.getValue().first, from)) {
                        e.getValue().second.offer(event);
                    }
                }
            });
        }

        private boolean deliver(StatusListener sl, Stream from) {
            if (sl.getStreamFilter() == null && (from instanceof StreamUser || from instanceof AutoReloadStream)) {
                return true;
            }
            else if (from instanceof FilterStream &&
                    ((FilterStream) from).contains(sl.getStreamFilter())) {
                return true;
            }
            else if (from instanceof RestStream &&
                    ((RestStream) from).getTag().equals(sl.getRestTag())) {
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
                            String.format("onStatus(Registered Listener %d): %s from %s [%s:%s]:@%s %s",
                                    statusListeners.syncReturn(List::size),
                                    StringUtil.formatDate(status.getCreatedAt()),
                                    from.getUserRecord().ScreenName,
                                    from.getClass().getSimpleName(),
                                    status.getClass().getSimpleName(),
                                    status.getUser().getScreenName(),
                                    status.getText()));
                    if (!(status instanceof FakeStatus)) {
                        userUpdateDelayer.enqueue(status.getUser());
                        if (status.isRetweet()) {
                            userUpdateDelayer.enqueue(status.getRetweetedStatus().getUser());
                        }
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
                            case AutoMuteConfig.MATCH_REGEX: {
                                Pattern pattern = autoMutePatternCache.get(config.getId());
                                if (pattern == null && autoMutePatternCache.indexOfKey(config.getId()) < 0) {
                                    try {
                                        pattern = Pattern.compile(config.getQuery());
                                        autoMutePatternCache.put(config.getId(), pattern);
                                    } catch (PatternSyntaxException ignore) {
                                        autoMutePatternCache.put(config.getId(), null);
                                    }
                                }
                                if (pattern != null) {
                                    Matcher matcher = pattern.matcher(preformedStatus.getText());
                                    match = matcher.find();
                                }
                                break;
                            }
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
                    PreformedStatus deliverStatus = new MetaStatus(preformedStatus, from.getClass().getSimpleName());
                    Pair<PreformedStatus, Long> standByStatus = retweetResponseStandBy.get(preformedStatus.getUser().getId());
                    if (new NotificationType(sharedPreferences.getInt("pref_notif_respond", 0)).isEnabled() &&
                            !preformedStatus.isRetweet() &&
                            standByStatus != null && !preformedStatus.getText().startsWith("@")) {
                        //Status is RT-Respond
                        retweetResponseStandBy.remove(preformedStatus.getUser().getId());
                        deliverStatus = new RespondNotifyStatus(preformedStatus, standByStatus.first);
                        if (!(from instanceof RestStream)) {
                            notifier.showNotification(R.integer.notification_respond, deliverStatus, deliverStatus.getUser());
                        }
                    } else if (standByStatus != null && standByStatus.second + RESPONSE_STAND_BY_EXPIRES < System.currentTimeMillis()) {
                        //期限切れ
                        retweetResponseStandBy.remove(preformedStatus.getUser().getId());
                    }
                    pushEventQueue(from, new StatusEventBuffer(user, deliverStatus, muted), false);

                    if (!(from instanceof RestStream)) {
                        if (status.isRetweet() &&
                                !mute[MuteConfig.MUTE_NOTIF_RT] &&
                                status.getRetweetedStatus().getUser().getId() == user.NumericId &&
                                checkOwn == null) {
                            notifier.showNotification(R.integer.notification_retweeted, preformedStatus, status.getUser());
                            createHistory(from, HistoryStatus.KIND_RETWEETED, status.getUser(), preformedStatus.getRetweetedStatus());

                            //Put Response Stand-By
                            retweetResponseStandBy.put(preformedStatus.getUser().getId(), Pair.create(preformedStatus, System.currentTimeMillis()));
                        } else if (!status.isRetweet() && !mute[MuteConfig.MUTE_NOTIF_MENTION]) {
                            UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                            for (UserMentionEntity ume : userMentionEntities) {
                                if (ume.getId() == user.NumericId) {
                                    notifier.showNotification(R.integer.notification_replied, preformedStatus, status.getUser());
                                }
                            }
                        }
                    }

                    HashtagEntity[] hashtagEntities = status.getHashtagEntities();
                    for (HashtagEntity he : hashtagEntities) {
                        hashCache.put("#" + he.getText());
                    }

                    receivedStatuses.put(preformedStatus.getId(), preformedStatus);
                    if (preformedStatus.getQuotedStatus() != null) {
                        receivedStatuses.put(preformedStatus.getQuotedStatusId(), new PreformedStatus(preformedStatus.getQuotedStatus(), user));
                    }
                    loadQuotedEntities(preformedStatus);

                    if (sharedPreferences.getBoolean("pref_exvoice_experimental_on_appear", false) && service != null) {
                        MRuby mRuby = service.getmRuby();
                        if (mRuby != null) {
                            Message message = StatusConverter.toMessage(mRuby, status);
                            try {
                                Plugin.call(mRuby, "appear", (Object) new Message[]{message});
                            } finally {
                                message.dispose();
                            }
                        }
                    }
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
