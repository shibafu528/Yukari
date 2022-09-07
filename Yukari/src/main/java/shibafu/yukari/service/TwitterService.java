package shibafu.yukari.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.collection.ArrayMap;
import androidx.collection.LongSparseArray;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.widget.Toast;

import okhttp3.Interceptor;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.AccountManagerImpl;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DatabaseEvent;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.Provider;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.database.UserExtrasManagerImpl;
import shibafu.yukari.linkage.ApiCollectionProvider;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.linkage.StatusLoader;
import shibafu.yukari.linkage.StreamChannel;
import shibafu.yukari.linkage.StreamCollectionProvider;
import shibafu.yukari.linkage.TimelineHub;
import shibafu.yukari.linkage.TimelineHubImpl;
import shibafu.yukari.linkage.TimelineHubQueue;
import shibafu.yukari.mastodon.DefaultVisibilityCache;
import shibafu.yukari.mastodon.MastodonApi;
import shibafu.yukari.mastodon.MastodonStream;
import shibafu.yukari.plugin.Pluggaloid;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.MissingTwitterInstanceException;
import shibafu.yukari.twitter.TwitterApi;
import shibafu.yukari.twitter.TwitterStream;
import shibafu.yukari.util.StringUtil;
import twitter4j.Twitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TwitterService extends Service implements ApiCollectionProvider, StreamCollectionProvider, AccountManager, UserExtrasManager {
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

    public class TweetReceiverBinder extends Binder {
        public TwitterService getService() {
            return TwitterService.this;
        }
    }

    //メインデータベース
    private CentralDatabase database;

    //キャッシュ
    private HashCache hashCache;
    private DefaultVisibilityCache defaultVisibilityCache;

    //ミュート判定
    private Suppressor suppressor;

    //Pluggaloid
    private Pluggaloid pluggaloid;

    //ネットワーク管理
    private LongSparseArray<ArrayMap<String, Boolean>> connectivityFlags = new LongSparseArray<>();

    //Timeline Pub/Sub
    private StatusLoader statusLoader;
    private TimelineHub timelineHub;

    //ユーザ情報
    private AccountManager accountManager;
    private UserExtrasManager userExtrasManager;

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
    private final BroadcastReceiver databaseUpdateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String className = intent.getStringExtra(DatabaseEvent.EXTRA_CLASS);
            if (MuteConfig.class.getName().equals(className)) {
                updateMuteConfig();
            } else if (AutoMuteConfig.class.getName().equals(className)) {
                updateAutoMuteConfig();
            }
        }
    };

    private Handler handler;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id_core_service))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Yukariを実行中です")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
                .setOngoing(true)
                .setLocalOnly(true)
                .setColor(ResourcesCompat.getColor(getResources(), R.color.key_color, null))
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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

        //キャッシュの読み込み
        hashCache = new HashCache(this);
        defaultVisibilityCache = new DefaultVisibilityCache(this);

        //Timeline Pub/Subのセットアップ
        TimelineHub hubImpl = new TimelineHubImpl(this, hashCache);
        timelineHub = new TimelineHubQueue(hubImpl);
        statusLoader = new StatusLoader(getApplicationContext(), timelineHub, this::getApiClient);

        //ユーザデータのロード
        accountManager = new AccountManagerImpl(getApplicationContext(), database, this, this, defaultVisibilityCache);
        userExtrasManager = new UserExtrasManagerImpl(database, accountManager.getUsers());

        //ミュート設定の読み込み
        suppressor = new Suppressor();
        updateMuteConfig();

        //オートミュート設定の読み込み
        updateAutoMuteConfig();

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

        //画像キャッシュの初期化
        BitmapCache.initialize(getApplicationContext());

        //ネットワーク状態の監視
        registerReceiver(streamConnectivityListener, new IntentFilter(ACTION_STREAM_CONNECTED));
        registerReceiver(streamConnectivityListener, new IntentFilter(ACTION_STREAM_DISCONNECTED));
        registerReceiver(balusListener, new IntentFilter("shibafu.yukari.BALUS"));

        // MRuby
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_enable_exvoice", false)) {
            pluggaloid = new Pluggaloid(getApplicationContext());
        }

        // イベント購読
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(databaseUpdateListener, new IntentFilter(DatabaseEvent.ACTION_UPDATE));

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

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(databaseUpdateListener);

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

        BitmapCache.dispose();

        hashCache.save(this);
        hashCache = null;

        defaultVisibilityCache.save(this);
        defaultVisibilityCache = null;

        database.close();
        database = null;

        if (pluggaloid != null) {
            pluggaloid.close();
        }

        stopForeground(true);

        CacheCleanerService.enqueueWork(this);

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

    public Suppressor getSuppressor() {
        return suppressor;
    }

    public HashCache getHashCache() {
        return hashCache;
    }

    public DefaultVisibilityCache getDefaultVisibilityCache() {
        return defaultVisibilityCache;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public UserExtrasManager getUserExtrasManager() {
        return userExtrasManager;
    }

    @Nullable
    public Pluggaloid getPluggaloid() {
        return pluggaloid;
    }

    //<editor-fold desc="AccountManager delegates">
    @Override
    public void reloadUsers() {
        accountManager.reloadUsers();
    }

    @Override
    @NonNull
    public List<AuthUserRecord> getUsers() {
        return accountManager.getUsers();
    }

    @Override
    @Nullable
    public AuthUserRecord getPrimaryUser() {
        return accountManager.getPrimaryUser();
    }

    @Override
    public void setPrimaryUser(long id) {
        accountManager.setPrimaryUser(id);
    }

    @Override
    public ArrayList<AuthUserRecord> getWriterUsers() {
        return accountManager.getWriterUsers();
    }

    @Override
    public void setWriterUsers(List<AuthUserRecord> writers) {
        accountManager.setWriterUsers(writers);
    }

    @Override
    public void setUserColor(long id, int color) {
        accountManager.setUserColor(id, color);
    }

    @Override
    public void storeUsers() {
        accountManager.storeUsers();
    }

    @Override
    public void deleteUser(long id) {
        accountManager.deleteUser(id);
    }

    @Override
    @Nullable
    public AuthUserRecord findPreferredUser(int apiType) {
        return accountManager.findPreferredUser(apiType);
    }
    //</editor-fold>

    //<editor-fold desc="UserExtrasManager delegates">
    @Override
    public void setColor(String url, int color) {
        userExtrasManager.setColor(url, color);
    }

    @Override
    public void setPriority(String url, AuthUserRecord userRecord) {
        userExtrasManager.setPriority(url, userRecord);
    }

    @Override
    @Nullable
    public AuthUserRecord getPriority(String url) {
        return userExtrasManager.getPriority(url);
    }

    @Override
    public List<UserExtras> getUserExtras() {
        return userExtrasManager.getUserExtras();
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
    @Override
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
    @Override
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
    @Override
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
    @Override
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
    @Override
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
    private void updateMuteConfig() {
        Log.d(LOG_TAG, "Update MuteConfig");
        suppressor.setConfigs(database.getRecords(MuteConfig.class));
    }

    private void updateAutoMuteConfig() {
        Log.d(LOG_TAG, "Update AutoMuteConfig");
        List<AutoMuteConfig> records = database.getRecords(AutoMuteConfig.class);
        timelineHub.setAutoMuteConfigs(records);
    }
    //</editor-fold>

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
