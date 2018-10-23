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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.widget.Toast;
import info.shibafu528.yukari.exvoice.MRuby;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.api.MikutterApi;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.Provider;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.linkage.StatusLoader;
import shibafu.yukari.linkage.StreamChannel;
import shibafu.yukari.linkage.TimelineHub;
import shibafu.yukari.mastodon.MastodonApi;
import shibafu.yukari.mastodon.MastodonStream;
import shibafu.yukari.plugin.AndroidCompatPlugin;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.MissingTwitterInstanceException;
import shibafu.yukari.twitter.TwitterApi;
import shibafu.yukari.twitter.TwitterStream;
import shibafu.yukari.util.StringUtil;
import twitter4j.DirectMessage;
import twitter4j.IDs;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterAPIConfiguration;
import twitter4j.TwitterException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TwitterService extends Service{
    private static final String LOG_TAG = "TwitterService";
    public static final String RELOADED_USERS = "shibafu.yukari.RELOADED_USERS";
    public static final String EXTRA_RELOAD_REMOVED = "removed";
    public static final String EXTRA_RELOAD_ADDED = "added";
    public static final String ACTION_STREAM_CONNECTED = "shibafu.yukari.STREAM_CONNECTED";
    public static final String ACTION_STREAM_DISCONNECTED = "shibafu.yukari.STREAM_DISCONNECTED";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_CHANNEL_ID = "channelId";
    public static final String EXTRA_CHANNEL_NAME = "channelName";

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

    //MRuby VM
    private MRuby mRuby;
    private Thread mRubyThread;
    private StringBuffer mRubyStdOut = new StringBuffer();
    private SimpleDateFormat mRubyStdOutFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    //ネットワーク管理
    private LongSparseArray<ArrayMap<String, Boolean>> connectivityFlags = new LongSparseArray<>();

    //Timeline Pub/Sub
    private StatusLoader statusLoader;
    private TimelineHub timelineHub;

    //ユーザ情報
    private List<AuthUserRecord> users = new ArrayList<>();
    private List<UserExtras> userExtras = new ArrayList<>();

    //API
    private ProviderApi[] providerApis = {
            new TwitterApi(),
            new MastodonApi()
    };

    //StreamAPI
    private ProviderStream[] providerStreams = {
            new TwitterStream(),
            new MastodonStream()
    };

    private BroadcastReceiver streamConnectivityListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_notif_connectivity", true)) {
                if (ACTION_STREAM_CONNECTED.equals(intent.getAction())) {
                    AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(EXTRA_USER);
                    ArrayMap<String, Boolean> channelFlags = connectivityFlags.get(userRecord.InternalId);
                    if (channelFlags != null) {
                        Boolean flag = channelFlags.get(intent.getStringExtra(EXTRA_CHANNEL_ID));
                        if (flag != null && !flag) {
                            showToast(intent.getStringExtra(EXTRA_CHANNEL_NAME) + " Connected @" + userRecord.ScreenName);
                            channelFlags.put(intent.getStringExtra(EXTRA_CHANNEL_ID), true);
                        }
                    }
                } else if (ACTION_STREAM_DISCONNECTED.equals(intent.getAction())) {
                    AuthUserRecord userRecord = (AuthUserRecord) intent.getSerializableExtra(EXTRA_USER);
                    showToast(intent.getStringExtra(EXTRA_CHANNEL_NAME) + " Disconnected @" + userRecord.ScreenName);

                    ArrayMap<String, Boolean> channelFlags = connectivityFlags.get(userRecord.InternalId);
                    if (channelFlags == null) {
                        channelFlags = new ArrayMap<>();
                        connectivityFlags.put(userRecord.InternalId, channelFlags);
                    }
                    channelFlags.put(intent.getStringExtra(EXTRA_CHANNEL_ID), false);
                }
            }
        }
    };
    private BroadcastReceiver balusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "バルス！！！！！！！", Toast.LENGTH_SHORT).show();
            timelineHub.onWipe();
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
                .setColor(ResourcesCompat.getColor(getResources(), R.color.key_color, null))
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        startForeground(R.string.app_name, builder.build());
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        handler = new Handler();

        //データベースのオープン
        database = new CentralDatabase(this).open();

        //Timeline Pub/Subのセットアップ
        timelineHub = new TimelineHub(this);
        statusLoader = new StatusLoader(getApplicationContext(), timelineHub, this::getApiClient);

        //ユーザデータのロード
        reloadUsers(true);

        //ユーザー設定の読み込み
        userExtras = database.getRecords(UserExtras.class, new Class[]{Collection.class}, users);

        //APIインスタンスの生成
        for (ProviderApi api : providerApis) {
            if (api != null) {
                api.onCreate(this);
            }
        }
        for (ProviderStream stream : providerStreams) {
            if (stream != null) {
                stream.onCreate(this);
            }
        }

        //ユーザデータのURLをセット
        for (AuthUserRecord user : users) {
            ProviderApi api = getProviderApi(user);
            if (api != null) {
                user.Url = api.getAccountUrl(user);
            }
        }

        //画像キャッシュの初期化
        BitmapCache.initialize(getApplicationContext());

        //ミュート設定の読み込み
        suppressor = new Suppressor();
        updateMuteConfig();

        //オートミュート設定の読み込み
        updateAutoMuteConfig();

        if (!users.isEmpty() && getPrimaryUser() != null) {
            final Twitter primaryAccount = getTwitterOrPrimary(null);

            //Configuration, Blocks, Mutes, No-Retweetsの取得
            new TwitterAsyncTask<Void>(getApplicationContext()) {
                @Override
                protected TwitterException doInBackground(Void... params) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                    try {
                        if (primaryAccount != null) {
                            apiConfiguration = primaryAccount.getAPIConfiguration();
                        }

                        if (sp.getBoolean("pref_filter_official", true) && users != null) {
                            ArrayList<AuthUserRecord> usersClone = new ArrayList<>(users);
                            for (AuthUserRecord userRecord : usersClone) {
                                Twitter twitter = getTwitter(userRecord);
                                if (twitter == null) {
                                    continue;
                                }

                                IDs ids = null;
                                try {
                                    do {
                                        ids = ids == null ? twitter.getBlocksIDs() : twitter.getBlocksIDs(ids.getNextCursor());

                                        if (suppressor == null) return null;
                                        suppressor.addBlockedIDs(ids.getIDs());
                                    } while (ids.hasNext());
                                } catch (TwitterException ignored) {}

                                try {
                                    long cursor = -1;
                                    do {
                                        ids = twitter.getMutesIDs(cursor);

                                        if (suppressor == null) return null;
                                        suppressor.addMutedIDs(ids.getIDs());

                                        cursor = ids.getNextCursor();
                                    } while (ids.hasNext());
                                } catch (TwitterException ignored) {}

                                try {
                                    ids = twitter.getNoRetweetsFriendships();

                                    if (suppressor == null) return null;
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
        registerReceiver(streamConnectivityListener, new IntentFilter(ACTION_STREAM_CONNECTED));
        registerReceiver(streamConnectivityListener, new IntentFilter(ACTION_STREAM_DISCONNECTED));
        registerReceiver(balusListener, new IntentFilter("shibafu.yukari.BALUS"));

        // MRuby
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_enable_exvoice", false)) {
            // MRuby VMの初期化
            mRuby = new MRuby(getApplicationContext());
            // 標準出力をLogcatとStringBufferにリダイレクト
            mRuby.setPrintCallback(value -> {
                if (value == null || value.length() == 0 || "\n".equals(value)) {
                    return;
                }
                Log.d("ExVoice (TS)", value);
                mRubyStdOut.append(mRubyStdOutFormat.format(new Date())).append(": ").append(value).append("\n");
            });
            // ブートストラップの実行およびバンドルRubyプラグインのロード
            mRuby.loadString("Android.require_assets 'bootstrap.rb'");
            // Javaプラグインのロード
            mRuby.registerPlugin(AndroidCompatPlugin.class);
            // ユーザプラグインのロード
            // TODO: ホワイトリストが必要だよねー
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File pluginDir = new File(getExternalFilesDir(null), "plugin");
                if (pluginDir.exists() && !pluginDir.isDirectory()) {
                    showToast("[Yukari exvoice]\nプラグインディレクトリのあるべき場所にファイルがあります。どかしていただけますか？");
                } else {
                    // プラグインディレクトリがなければ作っておく
                    if (!pluginDir.exists()) {
                        pluginDir.mkdirs();
                    }
                    // プラグインファイルを探索してロード
                    com.annimon.stream.Stream.of(pluginDir.listFiles(pathname -> pathname.isFile() && pathname.getName().endsWith(".rb")))
                            .forEach(value -> {
                                mRuby.printStringCallback("Require: " + value.getAbsolutePath());
                                mRuby.loadString("require '" + value.getAbsolutePath() + "'");
                            });
                }
            }
            // クロックの供給開始
            mRubyThread = new Thread(() -> {
                try {
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        mRuby.callTopLevelProc("tick");
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    Log.d("ExVoiceRunner", "Interrupt!");
                }
            }, "ExVoiceRunner");
            mRubyThread.start();
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

        for (ProviderStream stream : providerStreams) {
            if (stream != null) {
                stream.onDestroy();
            }
        }

        timelineHub.getHashCache().save(this);

        unregisterReceiver(streamConnectivityListener);
        unregisterReceiver(balusListener);

        statusLoader.cancelAll();
        statusLoader = null;
        timelineHub = null;

        for (ProviderApi api : providerApis) {
            if (api != null) {
                api.onDestroy();
            }
        }

        storeUsers();
        users = new ArrayList<>();
        userExtras = new ArrayList<>();

        BitmapCache.dispose();

        database.close();
        database = null;

        if (mRubyThread != null) {
            mRubyThread.interrupt();
            mRubyThread = null;
        }
        if (mRuby != null) {
            mRuby.close();
            mRuby = null;
        }

        stopForeground(true);

        startService(new Intent(this, CacheCleanerService.class));

        System.gc();

        Log.d(LOG_TAG, "onDestroy completed.");
    }

    public StatusLoader getStatusLoader() {
        return statusLoader;
    }

    public TimelineHub getTimelineHub() {
        return timelineHub;
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

    //<editor-fold desc="ユーザ情報管理">
    public void reloadUsers() {
        reloadUsers(false);
    }

    public void reloadUsers(boolean inInitialize) {
        Cursor cursor = database.getAccounts();
        List<AuthUserRecord> dbList = AuthUserRecord.getAccountsList(cursor);
        cursor.close();
        //消えたレコードの削除処理
        ArrayList<AuthUserRecord> removeList = new ArrayList<>();
        for (AuthUserRecord aur : users) {
            if (!dbList.contains(aur)) {
                if (!inInitialize) {
                    ProviderStream stream = getProviderStream(aur);
                    if (stream != null) {
                        stream.removeUser(aur);
                    }
                }
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
                if (!inInitialize) {
                    ProviderApi api = getProviderApi(aur);
                    if (api != null) {
                        aur.Url = api.getAccountUrl(aur);
                    }
                    ProviderStream stream = getProviderStream(aur);
                    if (stream != null) {
                        stream.addUser(aur);
                    }
                }
                Log.d(LOG_TAG, "Add user: @" + aur.ScreenName);
            } else {
                AuthUserRecord existRecord = users.get(users.indexOf(aur));
                existRecord.update(aur);
                Log.d(LOG_TAG, "Update user: @" + aur.ScreenName);
            }
        }
        Intent broadcastIntent = new Intent(RELOADED_USERS);
        broadcastIntent.putExtra(EXTRA_RELOAD_REMOVED, removeList);
        broadcastIntent.putExtra(EXTRA_RELOAD_ADDED, addedList);
        sendBroadcast(broadcastIntent);
        Log.d(LOG_TAG, "Reloaded users. User=" + users.size());
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
            userRecord.isPrimary = userRecord.InternalId == id;
        }
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
            if (user.InternalId == id) {
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
            if (aur.InternalId == id) {
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
    public void setColor(String url, int color) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                userExtra.setColor(color);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(url);
            extras.setColor(color);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    public void setPriority(String url, AuthUserRecord userRecord) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                userExtra.setPriorityAccount(userRecord);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(url);
            extras.setPriorityAccount(userRecord);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    @Nullable
    public AuthUserRecord getPriority(String url) {
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                return userExtra.getPriorityAccount();
            }
        }
        return null;
    }

    public List<UserExtras> getUserExtras() {
        return userExtras;
    }
    //</editor-fold>

    //<editor-fold desc="通信クライアントインスタンスの取得 (Legacy)">
    /**
     * 指定のアカウントに対応したAPIアクセスクライアントのインスタンスを取得します。
     * @param userRecord 認証情報。
     * @return APIアクセスクライアント。アカウントが所属するサービスに対応したものが返されます。
     */
    private Object getApiClient(@NonNull AuthUserRecord userRecord) {
        final ProviderApi api = getProviderApi(userRecord);
        if (api == null) {
            throw new RuntimeException("Invalid API Type : " + userRecord);
        }

        return api.getApiClient(userRecord);
    }

    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。結果はアカウントID毎にキャッシュされます。
     * @param userRecord 認証情報。ここに null を指定すると、AccessTokenの設定されていないインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。引数 userRecord が null の場合、AccessTokenは未設定。
     */
    @Nullable
    public Twitter getTwitter(@Nullable AuthUserRecord userRecord) {
        return (Twitter) getProviderApi(Provider.API_TWITTER).getApiClient(userRecord);
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
    //</editor-fold>

    //<editor-fold desc="Provider API">
    /**
     * 指定のアカウントに対応したAPIインスタンスを取得します。
     * @param userRecord 認証情報。
     * @return APIインスタンス。アカウントが所属するサービスに対応したものが返されます。
     */
    @Nullable
    public ProviderApi getProviderApi(@NonNull AuthUserRecord userRecord) {
        int apiType = userRecord.Provider.getApiType();
        if (0 <= apiType && apiType < providerApis.length) {
            return providerApis[apiType];
        }
        return null;
    }

    /**
     * 指定のAPI形式に対応したAPIインスタンスを取得します。
     * 対応するAPIインスタンスが定義されていない場合、例外をスローします。
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return APIインスタンス。
     * @throws UnsupportedOperationException 対応するAPIインスタンスが定義されていない場合にスロー
     */
    @NonNull
    public ProviderApi getProviderApi(int apiType) {
        if (0 <= apiType && apiType < providerApis.length) {
            return providerApis[apiType];
        }
        throw new UnsupportedOperationException("API Type " + apiType + " not implemented.");
    }
    //</editor-fold>

    //<editor-fold desc="Provider Stream API">
    /**
     * 指定のアカウントに対応したストリーミングAPIインスタンスを取得します。
     * @param userRecord 認証情報。
     * @return ストリーミングAPIインスタンス。アカウントが所属するサービスに対応したものが返されます。
     */
    @Nullable
    public ProviderStream getProviderStream(@NonNull AuthUserRecord userRecord) {
        int apiType = userRecord.Provider.getApiType();
        if (0 <= apiType && apiType < providerStreams.length) {
            return providerStreams[apiType];
        }
        return null;
    }

    /**
     * 指定のAPI形式に対応したストリーミングAPIインスタンスを取得します。
     * 対応するAPIインスタンスが定義されていない場合、例外をスローします。
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return ストリーミングAPIインスタンス。
     * @throws UnsupportedOperationException 対応するAPIインスタンスが定義されていない場合にスロー
     */
    @NonNull
    public ProviderStream getProviderStream(int apiType) {
        if (0 <= apiType && apiType < providerStreams.length) {
            return providerStreams[apiType];
        }
        throw new UnsupportedOperationException("API Type " + apiType + " not implemented.");
    }

    /**
     * 全てのストリーミングAPIインスタンスを取得します。
     * @return ストリーミングAPIインスタンス。
     */
    public ProviderStream[] getProviderStreams() {
        return providerStreams;
    }

    /**
     * ユーザによって有効化されているストリーミングチャンネルを全て起動します。
     */
    public void startStreamChannels() {
        for (ProviderStream stream : providerStreams) {
            if (stream != null) {
                stream.onStart();
            }
        }
    }

    /**
     * 現在接続されているストリーミングチャンネルを一度切断し、接続しなおします。
     */
    public void reconnectStreamChannels() {
        for (ProviderStream stream : providerStreams) {
            if (stream != null) {
                for (StreamChannel channel : stream.getChannels()) {
                    if (channel.isRunning()) {
                        channel.stop();
                        channel.start();
                    }
                }
            }
        }
    }
    //</editor-fold>

    //<editor-fold desc="MuteConfig Reload">
    public void updateMuteConfig() {
        suppressor.setConfigs(database.getRecords(MuteConfig.class));
    }

    public void updateAutoMuteConfig() {
        List<AutoMuteConfig> records = database.getRecords(AutoMuteConfig.class);
        timelineHub.setAutoMuteConfigs(records);
    }
    //</editor-fold>

    //<editor-fold desc="ツイート所有判定 (Legacy)">
    public AuthUserRecord isMyTweet(Status status) {
        return isMyTweet(status, false);
    }

    public AuthUserRecord isMyTweet(Status status, boolean checkOriginUser) {
        if (users == null) return null;
        if (checkOriginUser) {
            status = status.isRetweet() ? status.getRetweetedStatus() : status;
        }
        for (int i = 0; i < users.size(); ++i) {
            AuthUserRecord aur = users.get(i);
            if (aur.Provider.getApiType() == Provider.API_TWITTER && status.getUser().getId() == aur.NumericId) {
                return aur;
            }
        }
        return null;
    }

    public AuthUserRecord isMyTweet(DirectMessage message) {
        if (users == null) return null;
        for (int i = 0; i < users.size(); ++i) {
            AuthUserRecord aur = users.get(i);
            if (aur.Provider.getApiType() == Provider.API_TWITTER && message.getSenderId() == aur.NumericId) {
                return aur;
            }
        }
        return null;
    }
    //</editor-fold>

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

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(10000, TimeUnit.MILLISECONDS)
                    .addInterceptor(getUserAgentInterceptor())
                    .build();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://mikutter.hachune.net")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
            MikutterApi api = retrofit.create(MikutterApi.class);

            Response<List<MikutterApi.VersionInfo>> response = api.download(channel).execute();
            if (response.isSuccessful()) {
                List<MikutterApi.VersionInfo> info = response.body();

                if (!info.isEmpty()) {
                    return info.get(0).versionString;
                }
            }
        } catch (IOException ignore) {}
        return null;
    }

    public MRuby getmRuby() {
        return mRuby;
    }

    public StringBuffer getmRubyStdOut() {
        return mRubyStdOut;
    }

    public Interceptor getUserAgentInterceptor() {
        return chain -> chain.proceed(chain.request()
                .newBuilder()
                .header("User-Agent", StringUtil.getVersionInfo(TwitterService.this.getApplicationContext()))
                .build());
    }

    private void showToast(final String text) {
        handler.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }
}
