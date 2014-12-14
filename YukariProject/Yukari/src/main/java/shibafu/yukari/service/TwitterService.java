package shibafu.yukari.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.media.Pixiv;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.streaming.Stream;
import twitter4j.DirectMessage;
import twitter4j.IDs;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterAPIConfiguration;
import twitter4j.TwitterException;
import twitter4j.UploadedMedia;
import twitter4j.conf.ConfigurationBuilder;

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
    private TwitterAPIConfiguration apiConfiguration;

    public class TweetReceiverBinder extends Binder {
        public TwitterService getService() {
            return TwitterService.this;
        }
    }

    //メインデータベース
    private CentralDatabase database;

    //ミュート判定
    private Suppressor suppressor;

    //Proxy Server
    private Pixiv.PixivProxy pixivProxy;

    //ネットワーク管理

    //Twitter通信系
    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<>();
    private List<UserExtras> userExtras = new ArrayList<>();
    private StatusManager statusManager;
    private LongSparseArray<Boolean> connectivityFlags = new LongSparseArray<>();

    private BroadcastReceiver streamConnectivityListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Stream.CONNECTED_STREAM)) {
                AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(Stream.EXTRA_USER);
                if (connectivityFlags.get(userRecord.NumericId) != null) {
                    showToast(intent.getStringExtra(Stream.EXTRA_STREAM_TYPE) + "Stream Connected @" + userRecord.ScreenName);
                    connectivityFlags.put(userRecord.NumericId, true);
                }
            }
            else if (intent.getAction().equals(Stream.DISCONNECTED_STREAM)) {
                AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(Stream.EXTRA_USER);
                showToast(intent.getStringExtra(Stream.EXTRA_STREAM_TYPE) + "Stream Disconnected @" + userRecord.ScreenName);
                connectivityFlags.put(userRecord.NumericId, false);
            }
        }
    };
    private BroadcastReceiver balusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "バルス！！！！！！！", Toast.LENGTH_SHORT).show();
            StatusManager.getReceivedStatuses().clear();
            statusManager.sendFakeBroadcast("onWipe", new Class[]{});
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

        //画像キャッシュの初期化
        BitmapCache.initialize(getApplicationContext());

        //ミュート設定の読み込み
        suppressor = new Suppressor();
        suppressor.setConfigs(database.getRecords(MuteConfig.class));

        //ステータスマネージャのセットアップ
        statusManager = new StatusManager(this);

        //ユーザデータのロード
        reloadUsers();

        //ユーザー設定の読み込み
        userExtras = database.getRecords(UserExtras.class, new Class[]{Collection.class}, users);

        //Twitterインスタンスの生成
        twitter = TwitterUtil.getTwitterInstance(this);

        if (!users.isEmpty() && getPrimaryUser() != null) {
            //Configuration, Blocks, Mutes, No-Retweetsの取得
            new TwitterAsyncTask<Void>(getApplicationContext()) {
                @Override
                protected TwitterException doInBackground(Void... params) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                    try {
                        Twitter twitter = getTwitter();
                        apiConfiguration = twitter.getAPIConfiguration();

                        if (sp.getBoolean("pref_filter_official", true) && users != null) {
                            for (AuthUserRecord userRecord : users) {
                                twitter.setOAuthAccessToken(userRecord.getAccessToken());

                                IDs ids = null;
                                try {
                                    do {
                                        ids = ids == null ? twitter.getBlocksIDs() : twitter.getBlocksIDs(ids.getNextCursor());
                                        suppressor.addBlockedIDs(ids.getIDs());
                                    } while (ids.hasNext());
                                } catch (TwitterException ignored) {}

                                try {
                                    long cursor = -1;
                                    do {
                                        ids = twitter.getMutesIDs(cursor);
                                        suppressor.addMutedIDs(ids.getIDs());
                                        cursor = ids.getNextCursor();
                                    } while (ids.hasNext());
                                } catch (TwitterException ignored) {}

                                try {
                                    ids = twitter.getNoRetweetsFriendships();
                                    suppressor.addNoRetweetIDs(ids.getIDs());
                                } catch (TwitterException ignored) {}
                            }
                        }
                    } catch (TwitterException e) {
                        e.printStackTrace();
                        return e;
                    }
                    return null;
                }
            }.executeParallel();
        }

        //ネットワーク状態の監視
        registerReceiver(streamConnectivityListener, new IntentFilter(Stream.CONNECTED_STREAM));
        registerReceiver(streamConnectivityListener, new IntentFilter(Stream.DISCONNECTED_STREAM));
        registerReceiver(balusListener, new IntentFilter("shibafu.yukari.BALUS"));

        //Proxy Serverの起動
        try {
            pixivProxy = new Pixiv.PixivProxy();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(LOG_TAG, "onCreate completed.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        statusManager.getHashCache().save(this);

        unregisterReceiver(streamConnectivityListener);
        unregisterReceiver(balusListener);

        statusManager.shutdownAll();
        statusManager = null;

        twitter = null;

        storeUsers();
        users = null;
        userExtras = null;

        BitmapCache.dispose();

        database.close();
        database = null;

        if (pixivProxy != null) {
            pixivProxy.stop();
        }

        startService(new Intent(this, CacheCleanerService.class));

        System.gc();

        Log.d(LOG_TAG, "onDestroy completed.");
    }

    //<editor-fold desc="ユーザ情報管理">
    public void reloadUsers() {
        Cursor cursor = database.getAccounts();
        List<AuthUserRecord> dbList = AuthUserRecord.getAccountsList(cursor);
        cursor.close();
        //消えたレコードの削除処理
        ArrayList<AuthUserRecord> removeList = new ArrayList<>();
        for (AuthUserRecord aur : users) {
            if (!dbList.contains(aur)) {
                statusManager.stopUserStream(aur);
                removeList.add(aur);
                Log.d(LOG_TAG, "Remove user: @" + aur.ScreenName);
            }
        }
        users.removeAll(removeList);
        //新しいレコードの登録
        ArrayList<AuthUserRecord> addedList = new ArrayList<>();
        for (AuthUserRecord aur : dbList) {
            if (!users.contains(aur)) {
                addedList.add(aur);
                users.add(aur);
                if (aur.isActive) {
                    statusManager.startUserStream(aur);
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
        Log.d(LOG_TAG, "Reloaded users. User=" + users.size() +
                ", StreamUsers=" + statusManager.getStreamUsersCount());
    }

    public List<AuthUserRecord> getUsers() {
        return users != null ? users : new ArrayList<AuthUserRecord>();
    }

    public AuthUserRecord getPrimaryUser() {
        if (users != null) {
            for (AuthUserRecord userRecord : users) {
                if (userRecord.isPrimary) {
                    return userRecord;
                }
            }
        }
        return null;
    }

    public void setPrimaryUser(long id) {
        for (AuthUserRecord userRecord : users) {
            userRecord.isPrimary = userRecord.NumericId == id;
        }
        storeUsers();
        reloadUsers();
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        return statusManager.getActiveUsers();
    }

    public void setActiveUsers(ArrayList<AuthUserRecord> activeUsers) {
        ArrayList<AuthUserRecord> oldActiveList = getActiveUsers();
        ArrayList<AuthUserRecord> inactiveList = new ArrayList<>();
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
                statusManager.startUserStream(userRecord);
            }
            if (!userRecord.isActive && oldActiveList.contains(userRecord)) {
                //ストリーム停止
                statusManager.stopUserStream(userRecord);
            }
        }
        Intent broadcastIntent = new Intent(CHANGED_ACTIVE_STATE);
        broadcastIntent.putExtra(EXTRA_CHANGED_ACTIVE, activeUsers);
        broadcastIntent.putExtra(EXTRA_CHANGED_INACTIVE, inactiveList);
        sendBroadcast(broadcastIntent);
        storeUsers();
        reloadUsers();
    }

    public ArrayList<AuthUserRecord> getWriterUsers() {
        ArrayList<AuthUserRecord> writers = new ArrayList<>();
        for (AuthUserRecord userRecord : users) {
            if (userRecord.isWriter) {
                writers.add(userRecord);
            }
        }
        return writers;
    }

    public void setWriterUsers(List<AuthUserRecord> writers) {
        for (AuthUserRecord userRecord : users) {
            userRecord.isWriter = writers.contains(userRecord);
        }
        storeUsers();
    }

    public void setUserColor(long id, int color) {
        for (AuthUserRecord user : users) {
            if (user.NumericId == id) {
                user.AccountColor = color;
                break;
            }
        }
        storeUsers();
    }

    public void storeUsers() {
        database.beginTransaction();
        try {
            for (AuthUserRecord aur : users) {
                database.updateRecord(aur);
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
        database.deleteRecord(AuthUserRecord.class, id);
        //データベースからアカウントをリロードする
        reloadUsers();
    }
    //</editor-fold>

    //<editor-fold desc="UserExtras">
    public void setColor(long id, int color) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId() == id) {
                userExtra.setColor(color);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(id);
            extras.setColor(color);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    public void setPriority(long id, AuthUserRecord userRecord) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId() == id) {
                userExtra.setPriorityAccount(userRecord);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(id);
            extras.setPriorityAccount(userRecord);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    public List<UserExtras> getUserExtras() {
        return userExtras;
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
        List<String> hashCache = statusManager.getHashCache().getAll();
        return hashCache.toArray(new String[hashCache.size()]);
    }

    public Twitter getTwitter() {
        AuthUserRecord primaryUser = getPrimaryUser();
        if (primaryUser != null) {
            twitter.setOAuthAccessToken(primaryUser.getAccessToken());
        }
        return twitter;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public CentralDatabase getDatabase() {
        return database;
    }

    public TwitterAPIConfiguration getApiConfiguration() {
        return apiConfiguration;
    }

    public Suppressor getSuppressor() {
        return suppressor;
    }

    public void updateMuteConfig() {
        suppressor.setConfigs(database.getRecords(MuteConfig.class));
    }

    //<editor-fold desc="投稿操作系">
    public void postTweet(AuthUserRecord user, StatusUpdate status) throws TwitterException {
        if (user == null) {
            throw new IllegalArgumentException("送信元アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(user.getAccessToken());
        twitter.updateStatus(status);
    }

    public UploadedMedia uploadMedia(AuthUserRecord user, InputStream inputStream) throws TwitterException, IOException {
        File tempFile = File.createTempFile("uploadMedia", ".tmp", getExternalCacheDir());
        try {
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int length;
            try {
                while ((length = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }
            } finally {
                fos.close();
            }

            twitter.setOAuthAccessToken(user.getAccessToken());
            return twitter.uploadMedia(tempFile);
        }
        finally {
            tempFile.delete();
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
        if (user == null) {
            throw new IllegalArgumentException("操作対象アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(user.getAccessToken());
        try {
            twitter.retweetStatus(id);
            showToast("RTしました (@" + user.ScreenName + ")");
        } catch (TwitterException e) {
            e.printStackTrace();
            showToast("RTに失敗しました (@" + user.ScreenName + ")");
        }
    }

    public void createFavorite(AuthUserRecord user, long id){
        if (user == null) {
            throw new IllegalArgumentException("操作対象アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(user.getAccessToken());
        try {
            twitter.createFavorite(id);
            showToast("ふぁぼりました (@" + user.ScreenName + ")");
        } catch (TwitterException e) {
            e.printStackTrace();
            showToast("ふぁぼれませんでした (@" + user.ScreenName + ")");
        }
    }

    public void destroyFavorite(AuthUserRecord user, long id) {
        if (user == null) {
            throw new IllegalArgumentException("アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(user.getAccessToken());
        try {
            twitter.destroyFavorite(id);
            showToast("あんふぁぼしました (@" + user.ScreenName + ")");
        } catch (TwitterException e) {
            e.printStackTrace();
            showToast("あんふぁぼに失敗しました (@" + user.ScreenName + ")");
        }
    }

    public void destroyStatus(AuthUserRecord user, long id) {
        if (user == null) {
            throw new IllegalArgumentException("アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(user.getAccessToken());
        try {
            twitter.destroyStatus(id);
            showToast("ツイートを削除しました");
            return;
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        showToast("ツイート削除に失敗しました");
    }
    //</editor-fold>

    public AuthUserRecord isMyTweet(Status status) {
        if (users == null) return null;
        for (int i = 0; i < users.size(); ++i) {
            AuthUserRecord aur = users.get(i);
            if (status.getUser().getId() == aur.NumericId) {
                return aur;
            }
        }
        return null;
    }

    public AuthUserRecord isMyTweet(DirectMessage message) {
        if (users == null) return null;
        for (int i = 0; i < users.size(); ++i) {
            AuthUserRecord aur = users.get(i);
            if (message.getSenderId() == aur.NumericId) {
                return aur;
            }
        }
        return null;
    }

}
