package shibafu.yukari.twitter.statusmanager;

import android.util.Log;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.database.Provider;
import twitter4j.Status;
import twitter4j.User;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by shibafu on 2015/07/28.
 */
public class UserUpdateDelayer {
    private WeakReference<CentralDatabase> database;

    private Thread thread;
    private Queue<User> queue = new LinkedList<>();
    private final Object queueLock = new Object();
    private volatile boolean shutdown;

    public UserUpdateDelayer(CentralDatabase database) {
        this.database = new WeakReference<>(database);

        thread = new Thread(new Worker(), "UserUpdateDelayer");
        thread.start();
    }

    public void enqueue(User user) {
        synchronized (queueLock) {
            queue.offer(user);
        }
    }

    public void enqueue(Collection<User> users) {
        synchronized (queueLock) {
            for (User user : users) {
                queue.offer(user);
            }
        }
    }

    public void enqueue(Status status) {
        synchronized (queueLock) {
            queue.offer(status.getUser());
            if (status.isRetweet()) {
                queue.offer(status.getRetweetedStatus().getUser());
            }
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
                    database.get().beginTransaction();
                    try {
                        for (User user : work) {
                            database.get().updateRecord(new DBUser(user));
                            database.get().updateAccountProfile(Provider.TWITTER.getId(),
                                    user.getId(), user.getScreenName(), user.getName(),
                                    user.getOriginalProfileImageURLHttps());
                        }
                        database.get().setTransactionSuccessful();
                    } finally {
                        database.get().endTransaction();
                    }
                }

                try {
                    int time = work.size() * 4 + 100;
                    Thread.sleep(time);
                    if (time >= 110) {
                        Log.d("UserUpdateDelayer", "Next update is " + time + "ms later");
                    }

                    while (database.get() == null) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ignore) {
                }
            }
        }
    }
}
