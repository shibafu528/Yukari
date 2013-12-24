package shibafu.yukari.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.StreamUser;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.media.ImageUpload;
import twitter4j.media.ImageUploadFactory;
import twitter4j.media.MediaProvider;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TwitterService extends Service{
    private static final String LOG_TAG = "TwitterService";
    public static final String RELOADED_USERS = "shibafu.yukari.RELOADED_USERS";
    public static final String EXTRA_RELOAD_REMOVED = "removed";
    public static final String EXTRA_RELOAD_ADDED = "added";
    public static final String CHANGED_ACTIVE_STATE = "shibafu.yukari.CHANGED_ACTIVE_STATE";
    public static final String EXTRA_CHANGED_INACTIVE = "inactive";
    public static final String EXTRA_CHANGED_ACTIVE = "active";

    //Binder
    private final IBinder binder = new TweetReceiverBinder();

    public class TweetReceiverBinder extends Binder {
        public TwitterService getService() {
            return TwitterService.this;
        }
    }

    //メインデータベース
    private CentralDatabase database;

    //キャッシュ系
    private HashCache hashCache;

    //システムサービス系
    private NotificationManager notificationManager;
    private ConnectivityManager connectivityManager;
    private static final int NOTIF_FAVED = 1;
    private static final int NOTIF_REPLY = 2;
    private static final int NOTIF_RETWEET = 3;

    //ネットワーク管理
    private boolean disconnectedStream = false;

    //Twitter通信系
    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private List<StreamUser> streamUsers = new ArrayList<StreamUser>();
    private StreamUser.StreamListener listener = new StreamUser.StreamListener() {

        @Override
        public void onFavorite(AuthUserRecord from, User user, User user2, Status status) {
            database.updateUser(new DBUser(status.getUser()));
            database.updateUser(new DBUser(user));
            database.updateUser(new DBUser(user2));
            if (from.NumericId == user.getId())
                return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
            builder.setSmallIcon(R.drawable.ic_stat_favorite);
            builder.setContentTitle("Faved by @" + user.getScreenName());
            builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
            builder.setTicker("ふぁぼられ : @" + user.getScreenName());
            builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_fav"), AudioManager.STREAM_NOTIFICATION);
            builder.setAutoCancel(true);

            notificationManager.notify(NOTIF_FAVED, builder.build());
        }

        @Override
        public void onUnfavorite(AuthUserRecord from, User user, User user2, Status status) {

        }

        @Override
        public void onFollow(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {
            database.updateUser(new DBUser(directMessage.getSender()));
            for (StatusListener sl : statusListeners) {
                sl.onDirectMessage(from, directMessage);
            }
        }

        @Override
        public void onBlock(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onUnblock(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onStatus(AuthUserRecord from, Status status) {
            Log.d(LOG_TAG, "onStatus(Registered Listener " + statusListeners.size() + "): @" + status.getUser().getScreenName() + " " + status.getText());
            database.updateUser(new DBUser(status.getUser()));

            //自分のツイートかどうかマッチングを行う
            AuthUserRecord checkOwn = isMyTweet(status);
            if (checkOwn != null) {
                //自分のツイートであれば受信元は発信元アカウントということにする
                from = checkOwn;
            }

            PreformedStatus preformedStatus = new PreformedStatus(status, from);
            for (StatusListener sl : statusListeners) {
                sl.onStatus(from, preformedStatus);
            }

            //TODO: 自分自身のアカウントからは除外
            if (status.isRetweet() && status.getRetweetedStatus().getUser().getId() == from.NumericId) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                builder.setSmallIcon(R.drawable.ic_stat_retweet);
                builder.setContentTitle("Retweeted by @" + status.getUser().getScreenName());
                builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                builder.setTicker("RTされました : @" + status.getUser().getScreenName());
                builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_rt"), AudioManager.STREAM_NOTIFICATION);
                builder.setAutoCancel(true);

                notificationManager.notify(NOTIF_RETWEET, builder.build());
            }
            else {
                UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                for (UserMentionEntity ume : userMentionEntities) {
                    if (ume.getId() == from.NumericId) {
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                        builder.setSmallIcon(R.drawable.ic_stat_reply);
                        builder.setContentTitle("Reply from @" + status.getUser().getScreenName());
                        builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                        builder.setTicker("リプライ : @" + status.getUser().getScreenName());
                        builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), AudioManager.STREAM_NOTIFICATION);
                        builder.setAutoCancel(true);

                        notificationManager.notify(NOTIF_REPLY, builder.build());
                    }
                }
            }

            HashtagEntity[] hashtagEntities = status.getHashtagEntities();
            for (HashtagEntity he : hashtagEntities) {
                hashCache.put("#" + he.getText());
            }
        }
    };
    public interface StatusListener {
        public void onStatus(AuthUserRecord from, PreformedStatus status);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
    }
    private List<StatusListener> statusListeners = new ArrayList<StatusListener>();

    private BroadcastReceiver connectivityChangeListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isConnected;
            {
                NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                if (info == null) {
                    isConnected = false;
                }
                else {
                    isConnected = info.isConnected();
                }
            }
            if (isConnected && disconnectedStream) {
                //Stream再接続を行う
                Log.d("TwitterService", "Network connected.");
                showToast("UserStreamを再接続します");
                for (StreamUser streamUser : streamUsers) {
                    streamUser.start();
                }
                disconnectedStream = false;
            }
            if (!isConnected && !disconnectedStream) {
                Log.d("TwitterService", "Network disconnected.");
                for (StreamUser streamUser : streamUsers) {
                    streamUser.stop();
                }
                disconnectedStream = true;
            }
        }
    };

    private Handler handler;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        handler = new Handler();

        //データベースのオープン
        database = new CentralDatabase(this).open();

        //ユーザデータのロード
        reloadUsers();

        //Twitterインスタンスの生成
        twitter = TwitterUtil.getTwitterInstance(this);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(this);

        //システムサービスの取得
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        //ネットワーク状態の監視
        registerReceiver(connectivityChangeListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        Log.d(LOG_TAG, "onCreate completed.");
        //Toast.makeText(this, "Yukari Serviceを起動しました", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        unregisterReceiver(connectivityChangeListener);
        for (StreamUser su : streamUsers) {
            su.stop();
        }
        streamUsers.clear();
        streamUsers = null;
        statusListeners.clear();
        statusListeners = null;
        listener = null;
        twitter = null;

        storeUsers();
        users = null;

        hashCache.save(this);

        database.close();
        database = null;

        Log.d(LOG_TAG, "onDestroy completed.");
        //Toast.makeText(this, "Yukari Serviceを停止しました", Toast.LENGTH_SHORT).show();
    }

    public void addStatusListener(StatusListener l) {
        if (statusListeners != null && !statusListeners.contains(l))
            statusListeners.add(l);
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners != null && statusListeners.contains(l))
            statusListeners.remove(l);
    }

    //<editor-fold desc="ユーザ情報管理">
    public void reloadUsers() {
        Cursor cursor = database.getAccounts();
        List<AuthUserRecord> dbList = AuthUserRecord.getAccountsList(cursor);
        cursor.close();
        //消えたレコードの削除処理
        ArrayList<AuthUserRecord> removeList = new ArrayList<AuthUserRecord>();
        for (AuthUserRecord aur : users) {
            if (!dbList.contains(aur)) {
                StreamUser remove = null;
                for (StreamUser su : streamUsers) {
                    if (su.getUserRecord().equals(aur)) {
                        su.stop();
                        remove = su;
                        break;
                    }
                }
                if (remove != null) {
                    statusListeners.remove(remove);
                    streamUsers.remove(remove);
                }
                removeList.add(aur);
                Log.d(LOG_TAG, "Remove user: @" + aur.ScreenName);
            }
        }
        users.removeAll(removeList);
        //新しいレコードの登録
        ArrayList<AuthUserRecord> addedList = new ArrayList<AuthUserRecord>();
        for (AuthUserRecord aur : dbList) {
            if (!users.contains(aur)) {
                addedList.add(aur);
                users.add(aur);
                if (aur.isActive) {
                    StreamUser su = new StreamUser(this, aur);
                    su.setListener(listener);
                    streamUsers.add(su);
                    su.start();
                }
                Log.d(LOG_TAG, "Add user: @" + aur.ScreenName);
            }
            else {
                AuthUserRecord existRecord = users.get(users.indexOf(aur));
                existRecord.update(aur);
                Log.d(LOG_TAG, "Update user: @" + aur.ScreenName);
            }
        }
        Intent broadcastIntent = new Intent(RELOADED_USERS);
        broadcastIntent.putExtra(EXTRA_RELOAD_REMOVED, removeList);
        broadcastIntent.putExtra(EXTRA_RELOAD_ADDED, addedList);
        sendBroadcast(broadcastIntent);
        Log.d(LOG_TAG, "Reloaded users. User=" + users.size() + ", StreamUsers=" + streamUsers.size());
    }



    public List<AuthUserRecord> getUsers() {
        return users;
    }

    public AuthUserRecord getPrimaryUser() {
        for (AuthUserRecord userRecord : users) {
            if (userRecord.isPrimary) {
                return userRecord;
            }
        }
        return null;
    }

    public void setPrimaryUser(long id) {
        for (AuthUserRecord userRecord : users) {
            if (userRecord.NumericId == id) {
                userRecord.isPrimary = true;
            }
            else {
                userRecord.isPrimary = false;
            }
        }
        storeUsers();
        reloadUsers();
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        ArrayList<AuthUserRecord> activeUsers = new ArrayList<AuthUserRecord>();
        for (StreamUser su : streamUsers) {
            activeUsers.add(su.getUserRecord());
        }
        return activeUsers;
    }

    public void setActiveUsers(ArrayList<AuthUserRecord> activeUsers) {
        ArrayList<AuthUserRecord> oldActiveList = getActiveUsers();
        ArrayList<AuthUserRecord> inactiveList = new ArrayList<AuthUserRecord>();
        for (AuthUserRecord userRecord : users) {
            if (activeUsers.contains(userRecord)) {
                userRecord.isActive = true;
            }
            else {
                inactiveList.add(userRecord);
                userRecord.isActive = false;
            }

            if (userRecord.isActive && !oldActiveList.contains(userRecord)) {
                //ストリーム開始
                StreamUser su = new StreamUser(this, userRecord);
                su.setListener(listener);
                streamUsers.add(su);
                su.start();
            }
            if (!userRecord.isActive && oldActiveList.contains(userRecord)) {
                //ストリーム停止
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
        }
        Intent broadcastIntent = new Intent(CHANGED_ACTIVE_STATE);
        broadcastIntent.putExtra(EXTRA_CHANGED_ACTIVE, activeUsers);
        broadcastIntent.putExtra(EXTRA_CHANGED_INACTIVE, inactiveList);
        storeUsers();
        reloadUsers();
    }

    public ArrayList<AuthUserRecord> getWriterUsers() {
        ArrayList<AuthUserRecord> writers = new ArrayList<AuthUserRecord>();
        for (AuthUserRecord userRecord : users) {
            if (userRecord.isWriter) {
                writers.add(userRecord);
            }
        }
        return writers;
    }

    public void setWriterUsers(List<AuthUserRecord> writers) {
        for (AuthUserRecord userRecord : users) {
            if (writers.contains(userRecord)) {
                userRecord.isWriter = true;
            }
            else {
                userRecord.isWriter = false;
            }
        }
        storeUsers();
    }

    public void storeUsers() {
        database.beginTransaction();
        try {
            for (AuthUserRecord aur : users) {
                database.updateAccount(aur);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void deleteUser(long id) {
        //Primaryの委譲が必要か確認する
        boolean delegatePrimary = false;
        for (AuthUserRecord aur : users) {
            if (aur.NumericId == id) {
                delegatePrimary = aur.isPrimary;
            }
        }
        if (users.size() > 0 && delegatePrimary) {
            users.get(0).isPrimary = true;
        }
        //削除以外のこれまでの変更を保存しておく
        storeUsers();
        //実際の削除を行う
        database.deleteAccount(id);
        //データベースからアカウントをリロードする
        reloadUsers();
    }
    //</editor-fold>

    private void showToast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public ConfigurationBuilder getBuilder() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.setOAuthConsumerKey(getString(R.string.twitter_consumer_key));
        builder.setOAuthConsumerSecret(getString(R.string.twitter_consumer_secret));
        return builder;
    }

    public String[] getHashCache() {
        return hashCache.getAll().toArray(new String[0]);
    }

    public Twitter getTwitter() {
        AuthUserRecord primaryUser = getPrimaryUser();
        if (primaryUser != null) {
            twitter.setOAuthAccessToken(primaryUser.getAccessToken());
        }
        return twitter;
    }

    public CentralDatabase getDatabase() {
        return database;
    }

    //<editor-fold desc="投稿操作系">
    public void postTweet(AuthUserRecord user, StatusUpdate status) throws TwitterException {
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            twitter.updateStatus(status);
        }
        else {
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                twitter.updateStatus(status);
            }
        }
    }

    public void postTweet(AuthUserRecord user, StatusUpdate status, File[] media) throws  TwitterException {
        ConfigurationBuilder builder = getBuilder();
        if (user != null) {
            builder.setOAuthAccessToken(user.getAccessToken().getToken());
            builder.setOAuthAccessTokenSecret(user.getAccessToken().getTokenSecret());

            Configuration conf = builder.build();

            MediaProvider service = MediaProvider.TWITTER;
            StringBuilder urls = new StringBuilder(status.getStatus());
            for (File m : media) {
                ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                String url = upload.upload(m, status.getStatus());
                if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                    urls.append(" ");
                    urls.append(url);
                }
                else {
                    return;
                }
            }
            postTweet(user, new StatusUpdate(urls.toString()));
        }
        else {
            for (AuthUserRecord aur : users) {
                builder.setOAuthAccessToken(aur.getAccessToken().getToken());
                builder.setOAuthAccessTokenSecret(aur.getAccessToken().getTokenSecret());

                Configuration conf = builder.build();

                MediaProvider service = MediaProvider.TWITTER;
                StringBuilder urls = new StringBuilder(status.getStatus());
                boolean skip = false;
                for (File m : media) {
                    ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                    String url = upload.upload(m, status.getStatus());
                    if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                        urls.append(" ");
                        urls.append(url);
                    }
                    else {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                postTweet(user, new StatusUpdate(urls.toString()));
            }
        }
    }

    public void postTweet(AuthUserRecord user, StatusUpdate status, InputStream[] media) throws  TwitterException {
        ConfigurationBuilder builder = getBuilder();
        if (user != null) {
            builder.setOAuthAccessToken(user.getAccessToken().getToken());
            builder.setOAuthAccessTokenSecret(user.getAccessToken().getTokenSecret());

            Configuration conf = builder.build();

            MediaProvider service = MediaProvider.TWITTER;
            StringBuilder urls = new StringBuilder(status.getStatus());
            for (InputStream m : media) {
                ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                String url = upload.upload("image", m, status.getStatus());
                if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                    urls.append(" ");
                    urls.append(url);
                }
                else {
                    return;
                }
            }
            postTweet(user, new StatusUpdate(urls.toString()));
        }
        else {
            for (AuthUserRecord aur : users) {
                builder.setOAuthAccessToken(aur.getAccessToken().getToken());
                builder.setOAuthAccessTokenSecret(aur.getAccessToken().getTokenSecret());

                Configuration conf = builder.build();

                MediaProvider service = MediaProvider.TWITTER;
                StringBuilder urls = new StringBuilder(status.getStatus());
                boolean skip = false;
                for (InputStream m : media) {
                    ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                    String url = upload.upload("image", m, status.getStatus());
                    if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                        urls.append(" ");
                        urls.append(url);
                    }
                    else {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                postTweet(user, new StatusUpdate(urls.toString()));
            }
        }
    }

    public void sendDirectMessage(String to, AuthUserRecord from, String message) throws TwitterException {
        if (from == null) {
            throw new IllegalArgumentException("送信元アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(from.getAccessToken());
        twitter.sendDirectMessage(to, message);
    }

    public void retweetStatus(AuthUserRecord user, long id){
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                twitter.retweetStatus(id);
                showToast("RTしました (@" + user.ScreenName + ")");
            } catch (TwitterException e) {
                e.printStackTrace();
                showToast("RTに失敗しました (@" + user.ScreenName + ")");
            }
        }
        else {
            int success = 0, failed = 0;
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                try {
                    twitter.retweetStatus(id);
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
            showToast("RT成功: " + success + ((failed > 0)? " / RT失敗: " + failed: ""));
        }
    }

    public void createFavorite(AuthUserRecord user, long id){
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                twitter.createFavorite(id);
                showToast("ふぁぼりました (@" + user.ScreenName + ")");
            } catch (TwitterException e) {
                e.printStackTrace();
                showToast("ふぁぼれませんでした (@" + user.ScreenName + ")");
            }
        }
        else {
            int success = 0, failed = 0;
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                try {
                    twitter.createFavorite(id);
                    ++success;
                } catch (TwitterException e) {
                    e.printStackTrace();
                    ++failed;
                }
            }
            showToast("ふぁぼ成功: " + success + ((failed > 0)? " / ふぁぼ失敗: " + failed: ""));
        }
    }

    public void destroyStatus(AuthUserRecord user, long id) {
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                twitter.destroyStatus(id);
                showToast("ツイートを削除しました");
                return;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        }
        showToast("ツイート削除に失敗しました");
    }
    //</editor-fold>

    public AuthUserRecord isMyTweet(Status status) {
        if (users == null) return null;
        for (AuthUserRecord aur : users) {
            if (status.getUser().getId() == aur.NumericId) {
                return aur;
            }
        }
        return null;
    }

}
