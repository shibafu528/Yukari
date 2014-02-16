package shibafu.yukari.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
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
import shibafu.yukari.common.TabType;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.twitter.streaming.StreamUser;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;
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

    //Preference
    private SharedPreferences sharedPreferences;

    //キャッシュ系
    private HashCache hashCache;

    //システムサービス系
    private NotificationManager notificationManager;
    private ConnectivityManager connectivityManager;
    private Vibrator vibrator;
    private static final int NOTIF_FAVED = 1;
    private static final int NOTIF_REPLY = 2;
    private static final int NOTIF_RETWEET = 3;

    //バイブレーションパターン
    private long[] vibReply = {450, 130, 140, 150};
    private long[] vibRetweet = {150, 130, 300, 150};
    private long[] vibFaved = {140, 100};

    //ネットワーク管理
    private boolean disconnectedStream = false;

    //Twitter通信系
    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private List<StreamUser> streamUsers = new ArrayList<StreamUser>();
    private Map<String, FilterStream> filterMap = new HashMap<String, FilterStream>();
    private StreamListener listener = new StreamListener() {

        @Override
        public void onFavorite(Stream from, User user, User user2, Status status) {
            database.updateUser(new DBUser(status.getUser()));
            database.updateUser(new DBUser(user));
            database.updateUser(new DBUser(user2));
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
            database.updateUser(new DBUser(directMessage.getSender()));
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
            if (from == null || status == null) {
                Log.w(LOG_TAG, "onStatus, NullPointer!");
                return;
            }
            Log.d(LOG_TAG, "onStatus(Registered Listener " + statusListeners.size() + "): @" + status.getUser().getScreenName() + " " + status.getText());
            database.updateUser(new DBUser(status.getUser()));
            AuthUserRecord user = from.getUserRecord();

            //自分のツイートかどうかマッチングを行う
            AuthUserRecord checkOwn = isMyTweet(status);
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
                        pattern = vibReply;
                        break;
                    case NOTIF_RETWEET:
                        icon = R.drawable.ic_stat_retweet;
                        titleHeader = "Retweeted by @";
                        tickerHeader = "RTされました : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_rt");
                        pattern = vibRetweet;
                        break;
                    case NOTIF_FAVED:
                        icon = R.drawable.ic_stat_favorite;
                        titleHeader = "Faved by @";
                        tickerHeader = "ふぁぼられ : @";
                        sound = Uri.parse("android.resource://shibafu.yukari/raw/se_fav");
                        pattern = vibFaved;
                        break;
                }
                if (notificationType.getNotificationType() == NotificationType.TYPE_NOTIF) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                    builder.setSmallIcon(icon);
                    builder.setContentTitle(titleHeader + actionBy.getScreenName());
                    builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                    builder.setTicker(tickerHeader + actionBy.getScreenName());
                    if (notificationType.isUseSound()) {
                        builder.setSound(sound, AudioManager.STREAM_NOTIFICATION);
                    }
                    if (notificationType.isUseVibration()) {
                        vibrator.vibrate(pattern, -1);
                    }
                    builder.setAutoCancel(true);
                    switch (category) {
                        case NOTIF_REPLY:
                        {
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                    getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
                            builder.setContentIntent(pendingIntent);
                            break;
                        }
                    }
                    notificationManager.notify(category, builder.build());
                }
                else {
                    if (notificationType.isUseSound()) {
                        MediaPlayer mediaPlayer = MediaPlayer.create(TwitterService.this, sound);
                        mediaPlayer.start();
                    }
                    if (notificationType.isUseVibration()) {
                        vibrator.vibrate(pattern, -1);
                    }
                    final String text = tickerHeader + actionBy.getScreenName() + "\n" +
                            status.getUser().getScreenName() + ": " + status.getText();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    text,
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
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
                showToast("Streamを再接続します");
                for (StreamUser streamUser : streamUsers) {
                    streamUser.start();
                }
                for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
                    e.getValue().start();
                }
                disconnectedStream = false;
            }
            if (!isConnected && !disconnectedStream) {
                Log.d("TwitterService", "Network disconnected.");
                for (StreamUser streamUser : streamUsers) {
                    streamUser.stop();
                }
                for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
                    e.getValue().stop();
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

        //SharedPreferenceの取得
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        //ユーザデータのロード
        reloadUsers();

        //Twitterインスタンスの生成
        twitter = TwitterUtil.getTwitterInstance(this);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(this);

        //システムサービスの取得
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        //ネットワーク状態の監視
        registerReceiver(connectivityChangeListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        Log.d(LOG_TAG, "onCreate completed.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }

        unregisterReceiver(connectivityChangeListener);
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
        twitter = null;

        storeUsers();
        users = null;

        hashCache.save(this);

        database.close();
        database = null;

        Log.d(LOG_TAG, "onDestroy completed.");
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

    public void startFilterStream(String query, AuthUserRecord manager) {
        if (!filterMap.containsKey(query)) {
            FilterStream filterStream = new FilterStream(this, manager, query);
            filterStream.setListener(listener);
            filterMap.put(query, filterStream);
            filterStream.start();
            showToast("Start FilterStream:" + query);
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
        builder.setUseSSL(true);
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
