package shibafu.yukari.service;

import android.R;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import shibafu.yukari.activity.NotFoundStubActivity;
import shibafu.yukari.database.FriendsDBHelper;
import shibafu.yukari.receiver.StubReceiver;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/15.
 */
public class FriendsDBUpdateService extends IntentService {

    public static final String NOTICE_UPDATED = "shibafu.yukari.NOTICE_UPDATED";

    private TwitterService service;
    private boolean serviceBound = false;

    private NotificationManager notificationManager;
    private static final int NOTIFICATION_ID = 127;

    public FriendsDBUpdateService() {
        super("FriendsDBUpdateService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setTicker("キャッシュの更新中");
        builder.setContentTitle("Yukari ネームキャッシュ");
        builder.setContentText("スクリーンネームを取得中");
        builder.setWhen(System.currentTimeMillis());
        builder.setOngoing(true);
        builder.setSmallIcon(R.drawable.stat_sys_download);
        builder.setProgress(100, 0, false);
        builder.setContentIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, StubReceiver.class), 0));

        notificationManager.notify(NOTIFICATION_ID, builder.build());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Twitter twitter = service.getTwitter();
        List<AuthUserRecord> users = service.getUsers();
        Set<User> received = new HashSet<User>();
        int usersSize = users.size();
        for (int i = 0; i < usersSize; i++) {
            builder.setContentText("スクリーンネームを取得中 " + received.size() + "件取得");
            builder.setProgress(usersSize, i, false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());

            AuthUserRecord user = users.get(i);
            twitter.setOAuthAccessToken(user.getAccessToken());
            received.addAll(getFollowingUsers(twitter));
            received.addAll(getFollowerUsers(twitter));
        }

        builder.setContentText("スクリーンネームを取得中 " + received.size() + "件取得");
        builder.setProgress(usersSize, 0, false);
        notificationManager.notify(NOTIFICATION_ID, builder.build());

        FriendsDBHelper db = new FriendsDBHelper(this);
        db.open();
        db.beginTransaction();
        try {
            User[] receivedArray = received.toArray(new User[0]);
            int receivedSize = receivedArray.length;
            for (int i = 0; i < receivedSize; i++) {
                builder.setProgress(receivedSize, i, false);
                builder.setContentText("スクリーンネームを保存中 " + i + "/" + received.size());
                notificationManager.notify(NOTIFICATION_ID, builder.build());

                db.saveRecord(receivedArray[i]);
            }

            db.successTransaction();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }
        builder.setSmallIcon(R.drawable.stat_sys_download_done);
        builder.setProgress(usersSize, usersSize, false);
        builder.setContentText("完了しました " + received.size() + "件更新");
        builder.setTicker("キャッシュ更新完了");
        builder.setOngoing(false);
        builder.setAutoCancel(true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private List<User> getFollowingUsers(Twitter twitter) {
        PagableResponseList<User> responseList = null;
        List<User> returnList = new ArrayList<User>();

        long cursor = -1;
        while (true) {
            try {
                responseList = twitter.getFriendsList(twitter.getScreenName(), cursor);
            } catch (TwitterException e) {
                e.printStackTrace();
                break;
            }
            if (responseList == null || responseList.isEmpty()) {
                break;
            }
            returnList.addAll(responseList);
            if (!responseList.hasNext()) {
                break;
            }

            cursor = responseList.getNextCursor();
        }
        return returnList;
    }

    private List<User> getFollowerUsers(Twitter twitter) {
        PagableResponseList<User> responseList = null;
        List<User> returnList = new ArrayList<User>();

        long cursor = -1;
        while (true) {
            try {
                responseList = twitter.getFollowersList(twitter.getScreenName(), cursor);
            } catch (TwitterException e) {
                e.printStackTrace();
                break;
            }
            if (responseList == null || responseList.isEmpty()) {
                break;
            }
            returnList.addAll(responseList);
            if (!responseList.hasNext()) {
                break;
            }

            cursor = responseList.getNextCursor();
        }
        return returnList;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            FriendsDBUpdateService.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
