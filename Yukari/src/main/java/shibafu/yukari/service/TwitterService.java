package shibafu.yukari.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.media.Pixiv;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.MissingTwitterInstanceException;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.util.StringUtil;
import twitter4j.DirectMessage;
import twitter4j.IDs;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterAPIConfiguration;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UploadedMedia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private TwitterFactory twitterFactory;
    private LongSparseArray<Twitter> twitterInstances = new LongSparseArray<>();
    private List<AuthUserRecord> users = new ArrayList<>();
    private List<UserExtras> userExtras = new ArrayList<>();
    private StatusManager statusManager;
    private LongSparseArray<Boolean> connectivityFlags = new LongSparseArray<>();

    private BroadcastReceiver streamConnectivityListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_notif_connectivity", true)) {
                if (intent.getAction().equals(Stream.CONNECTED_STREAM)) {
                    AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(Stream.EXTRA_USER);
                    if (connectivityFlags.get(userRecord.NumericId) != null) {
                        showToast(intent.getStringExtra(Stream.EXTRA_STREAM_TYPE) + "Stream Connected @" + userRecord.ScreenName);
                        connectivityFlags.put(userRecord.NumericId, true);
                    }
                } else if (intent.getAction().equals(Stream.DISCONNECTED_STREAM)) {
                    AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(Stream.EXTRA_USER);
                    showToast(intent.getStringExtra(Stream.EXTRA_STREAM_TYPE) + "Stream Disconnected @" + userRecord.ScreenName);
                    connectivityFlags.put(userRecord.NumericId, false);
                }
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Yukariを実行中です")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
                .setOngoing(true)
                .setLocalOnly(true)
                .setColor(getResources().getColor(R.color.key_color))
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        startForeground(R.string.app_name, builder.build());
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        handler = new Handler();

        //TwitterFactoryの生成
        twitterFactory = TwitterUtil.getTwitterFactory(this);

        //データベースのオープン
        database = new CentralDatabase(this).open();

        //画像キャッシュの初期化
        BitmapCache.initialize(getApplicationContext());

        //ミュート設定の読み込み
        suppressor = new Suppressor();
        updateMuteConfig();

        //ステータスマネージャのセットアップ
        statusManager = new StatusManager(this);

        //オートミュート設定の読み込み
        updateAutoMuteConfig();

        //ユーザデータのロード
        reloadUsers();

        //ユーザー設定の読み込み
        userExtras = database.getRecords(UserExtras.class, new Class[]{Collection.class}, users);

        if (!users.isEmpty() && getPrimaryUser() != null) {
            //Configuration, Blocks, Mutes, No-Retweetsの取得
            new TwitterAsyncTask<Void>(getApplicationContext()) {
                @Override
                protected TwitterException doInBackground(Void... params) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                    try {
                        {
                            Twitter primary = getTwitterOrPrimary(null);
                            if (primary != null) {
                                apiConfiguration = primary.getAPIConfiguration();
                            }
                        }

                        if (sp.getBoolean("pref_filter_official", true) && users != null) {
                            for (AuthUserRecord userRecord : users) {
                                Twitter twitter = getTwitter(userRecord);
                                if (twitter == null) {
                                    continue;
                                }

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

        //mikutter更新通知
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("mikutter_stable_notify", false)) {
            new SimpleAsyncTask() {

                @Override
                protected Void doInBackground(Void... params) {
                    String versionString = getLatestMikutterVersion("stable");
                    if (versionString != null) {
                        Log.d("mikutter-version", versionString);
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_stat_favorite)
                                .setContentTitle("mikutter " + versionString)
                                .setContentText("mikutter " + versionString + " がリリースされています。")
                                .setTicker("mikutter " + versionString)
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setAutoCancel(true)
                                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0,
                                        new Intent(Intent.ACTION_VIEW, Uri.parse("http://mikutter.hachune.net/download")),
                                        PendingIntent.FLAG_CANCEL_CURRENT));
                        NotificationManagerCompat.from(getApplicationContext()).notify(R.string.app_name, builder.build());
                    } else {
                        Log.d("mikutter-version", "null");
                    }
                    return null;
                }

            }.executeParallel();
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

        twitterInstances.clear();
        twitterFactory = null;

        storeUsers();
        users = new ArrayList<>();
        userExtras = new ArrayList<>();

        BitmapCache.dispose();

        database.close();
        database = null;

        if (pixivProxy != null) {
            pixivProxy.stop();
        }

        stopForeground(true);

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
        return users != null ? users : new ArrayList<>();
    }

    public AuthUserRecord getPrimaryUser() {
        if (users != null && !users.isEmpty()) {
            for (AuthUserRecord userRecord : users) {
                if (userRecord.isPrimary) {
                    return userRecord;
                }
            }
            return users.get(0);
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
        handler.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public String[] getHashCache() {
        List<String> hashCache = statusManager.getHashCache().getAll();
        return hashCache.toArray(new String[hashCache.size()]);
    }

    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。結果はアカウントID毎にキャッシュされます。
     * @param userRecord 認証情報。ここに null を指定すると、AccessTokenの設定されていないインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。引数 userRecord が null の場合、AccessTokenは未設定。
     */
    @Nullable
    public Twitter getTwitter(@Nullable AuthUserRecord userRecord) {
        if (twitterFactory == null) {
            return null;
        }

        if (userRecord == null) {
            return twitterFactory.getInstance();
        }

        if (twitterInstances.indexOfKey(userRecord.NumericId) < 0) {
            twitterInstances.put(userRecord.NumericId, twitterFactory.getInstance(userRecord.getAccessToken()));
        }
        return twitterInstances.get(userRecord.NumericId);
    }

    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。
     * {@link #getTwitter(AuthUserRecord)} との違いは、こちらは引数 userRecord が null の場合、プライマリユーザでの取得を試みることです。
     * @param userRecord 認証情報。ここに null を指定すると、プライマリユーザのインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。
     */
    @Nullable
    public Twitter getTwitterOrPrimary(@Nullable AuthUserRecord userRecord) {
        if (userRecord == null) {
            return getTwitter(getPrimaryUser());
        } else {
            return getTwitter(userRecord);
        }
    }

    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。
     * {@link #getTwitter(AuthUserRecord)} との違いは、こちらはインスタンスの取得に失敗した際、例外をスローすることです。
     * @param userRecord 認証情報。ここに null を指定すると、AccessTokenの設定されていないインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。引数 userRecord が null の場合、AccessTokenは未設定。
     */
    @NonNull
    public Twitter getTwitterOrThrow(@Nullable AuthUserRecord userRecord) throws MissingTwitterInstanceException {
        Twitter twitter = getTwitter(userRecord);
        if (twitter == null) {
            throw new MissingTwitterInstanceException("Twitter インスタンスの取得エラー");
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

    public void updateAutoMuteConfig() {
        statusManager.setAutoMuteConfigs(database.getRecords(AutoMuteConfig.class));
    }

    //<editor-fold desc="投稿操作系">
    public void postTweet(AuthUserRecord user, StatusUpdate status) throws TwitterException {
        if (user == null) {
            throw new IllegalArgumentException("送信元アカウントが指定されていません");
        }
        Twitter twitter = getTwitter(user);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
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

            Twitter twitter = getTwitter(user);
            if (twitter == null) {
                throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
            }

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
        Twitter twitter = getTwitter(from);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
        twitter.sendDirectMessage(to, message);
    }

    public void retweetStatus(AuthUserRecord user, long id){
        if (user == null) {
            throw new IllegalArgumentException("操作対象アカウントが指定されていません");
        }
        Twitter twitter = getTwitter(user);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
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
        Twitter twitter = getTwitter(user);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
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
        Twitter twitter = getTwitter(user);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
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
        Twitter twitter = getTwitter(user);
        if (twitter == null) {
            throw new IllegalStateException("Twitterとの通信の準備に失敗しました");
        }
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

    private static class MikutterDownload {
        static class Version {
            public int major, minor, teeny, build;
            public String meta;
        }
        @SerializedName("version_string") public String versionString;
        public Version version;
        public String created;
        public String url;
    }

    /**
     * mikutterの最新バージョン情報を取得します。
     * @param channel リリースの区分。nullにした場合は "stable" 扱いにします。
     * @return 最新バージョン(基本的にはx.y.z alpha版などそれ以外のケースも有る)
     */
    @WorkerThread
    @Nullable
    private String getLatestMikutterVersion(@Nullable String channel) {
        if (channel == null) {
            channel = "stable";
        }
        try {
            Debug.waitForDebugger();

            HttpURLConnection conn = (HttpURLConnection) new URL("http://mikutter.hachune.net/download/" + channel + ".json").openConnection();
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", StringUtil.getVersionInfo(this));
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            try {
                String s;
                while ((s = br.readLine()) != null) {
                    sb.append(s);
                }
            } finally {
                br.close();
                conn.disconnect();
            }

            MikutterDownload[] info = new Gson().fromJson(sb.toString(), MikutterDownload[].class);

            if (info.length == 0) {
                return null;
            } else {
                return info[0].versionString;
            }

        } catch (IOException ignore) {}
        return null;
    }
}
