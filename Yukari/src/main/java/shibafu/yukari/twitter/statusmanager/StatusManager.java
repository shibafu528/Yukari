package shibafu.yukari.twitter.statusmanager;

import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import info.shibafu528.yukari.processor.autorelease.AutoRelease;
import info.shibafu528.yukari.processor.autorelease.AutoReleaser;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
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
import twitter4j.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager implements Releasable {
    private static final boolean PUT_STREAM_LOG = false;

    private static final String LOG_TAG = "StatusManager";

    @AutoRelease TwitterService service;
    @AutoRelease TimelineHub hub;

    private Handler handler = new Handler();

    //ステータス
    private boolean isStarted;

    //Streaming
    private List<StreamUser> streamUsers = new ArrayList<>();
    private Map<AuthUserRecord, FilterStream> filterMap = new HashMap<>();
    private StreamListenerEx listener = new StreamListenerEx();

    public StatusManager(TwitterService service) {
        this.service = service;
        this.hub = service.getTimelineHub();
    }

    @Override
    public void release() {
        streamUsers.clear();
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
        StreamUser su = new StreamUser(service, userRecord);
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
        FilterStream filterStream = FilterStream.getInstance(service, manager);
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

    private void showToast(final String text) {
        handler.post(() -> Toast.makeText(service.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
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
                    if (from == null || status == null || status.getUser() == null) {
                        if (PUT_STREAM_LOG) Log.d(LOG_TAG, "onStatus, NullPointer!");
                        return;
                    }
                    if (PUT_STREAM_LOG) Log.d(LOG_TAG,
                            String.format("onStatus: %s from %s [%s:%s]:@%s %s",
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
