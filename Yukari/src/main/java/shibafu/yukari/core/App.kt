package shibafu.yukari.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import okhttp3.OkHttpClient
import shibafu.yukari.R
import shibafu.yukari.common.HashCache
import shibafu.yukari.common.NotificationChannelPrefix
import shibafu.yukari.common.Suppressor
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.okhttp.UserAgentInterceptor
import shibafu.yukari.database.AccountManager
import shibafu.yukari.database.AccountManagerImpl
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.DatabaseEvent
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.Provider
import shibafu.yukari.database.UserExtrasManager
import shibafu.yukari.database.UserExtrasManagerImpl
import shibafu.yukari.linkage.ApiCollectionProvider
import shibafu.yukari.linkage.LifecycleStreamManager
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StatusLoader
import shibafu.yukari.linkage.StreamCollectionProvider
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.linkage.TimelineHubImpl
import shibafu.yukari.linkage.TimelineHubProvider
import shibafu.yukari.linkage.TimelineHubQueue
import shibafu.yukari.mastodon.DefaultVisibilityCache
import shibafu.yukari.mastodon.FetchDefaultVisibilityTask
import shibafu.yukari.mastodon.MastodonApi
import shibafu.yukari.media2.Media
import shibafu.yukari.service.CacheCleanerService
import shibafu.yukari.twitter.TwitterApi
import shibafu.yukari.twitter.TwitterProvider
import shibafu.yukari.util.CompatUtil

/**
 * Created by shibafu on 2015/08/29.
 */
class App : Application(), TimelineHubProvider, ApiCollectionProvider, StreamCollectionProvider, TwitterProvider {
    val database: CentralDatabase by lazy { CentralDatabase(this).open() }
    val accountManager: AccountManager by lazy { AccountManagerImpl(this, database) }
    val userExtrasManager: UserExtrasManager by lazy { UserExtrasManagerImpl(database, accountManager.users) }
    val suppressor: Suppressor by lazy { Suppressor().apply { configs = database.getRecords(MuteConfig::class.java) } }
    val hashCache: HashCache by lazy { HashCache(this) }
    val defaultVisibilityCache: DefaultVisibilityCache by lazy { DefaultVisibilityCache(this) }

    override val timelineHub: TimelineHub by lazy {
        TimelineHubQueue(TimelineHubImpl(this, this, accountManager, database, suppressor, this, hashCache)).apply {
            setAutoMuteConfigs(database.getRecords(AutoMuteConfig::class.java))
        }
    }

    val statusLoader: StatusLoader by lazy {
        StatusLoader(this, timelineHub) { userRecord ->
            val api = getProviderApi(userRecord) ?: throw RuntimeException("Invalid API Type : $userRecord")
            api.getApiClient(userRecord)
        }
    }

    lateinit var okhttpClient: OkHttpClient

    private val providerApis = arrayOf(TwitterApi(), MastodonApi())
    private val streamManager by lazy { LifecycleStreamManager(this) }

    private val databaseUpdateListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(DatabaseEvent.EXTRA_CLASS)) {
                MuteConfig::class.java.name -> {
                    Log.d(LOG_TAG, "Update MuteConfig")
                    suppressor.configs = database.getRecords(MuteConfig::class.java)
                }
                AutoMuteConfig::class.java.name -> {
                    Log.d(LOG_TAG, "Update AutoMuteConfig")
                    timelineHub.setAutoMuteConfigs(database.getRecords(AutoMuteConfig::class.java))
                }
            }
        }
    }

    private val userReloadListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val removedUsers = intent.getSerializableExtra(AccountManager.EXTRA_RELOAD_REMOVED) as java.util.ArrayList<AuthUserRecord>
            removedUsers.forEach { userRecord ->
                streamManager.onRemoveUser(userRecord)
            }

            val addedUsers = intent.getSerializableExtra(AccountManager.EXTRA_RELOAD_ADDED) as java.util.ArrayList<AuthUserRecord>
            addedUsers.forEach { userRecord ->
                streamManager.onAddUser(userRecord)

                if (userRecord.Provider.apiType == Provider.API_MASTODON) {
                    FetchDefaultVisibilityTask(this@App, defaultVisibilityCache, userRecord).executeParallel()
                }
            }
        }
    }

    private val balusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Toast.makeText(applicationContext, "バルス！！！！！！！", Toast.LENGTH_SHORT).show()
            timelineHub.onWipe()
        }
    }

    override fun onCreate() {
        super.onCreate()

        installSecurityProvider()

        // OkHttpの初期化
        initOkHttpClient()

        // 通知チャンネルの作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            createCommonNotificationChannels(nm)
            createAllAccountNotificationChannels(nm)
        }

        // 設定のマイグレーション
        migratePreference()

        // BroadcastReceiverの登録
        registerReceiver(balusListener, IntentFilter("shibafu.yukari.BALUS"))
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(databaseUpdateListener, IntentFilter(DatabaseEvent.ACTION_UPDATE))
        localBroadcastManager.registerReceiver(userReloadListener, IntentFilter(AccountManager.ACTION_RELOADED_USERS))

        // ライフサイクルイベントのリスナー登録
        val processLifecycleOwner = ProcessLifecycleOwner.get()
        processLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                Log.d(LOG_TAG, "[onStop] start")

                // わざわざ止める必要もないという説はある
                Log.d(LOG_TAG, "[onStop] cancel all status loader tasks")
                statusLoader.cancelAll()

                Log.d(LOG_TAG, "[onStop] enqueue cache cleaner service")
                CacheCleanerService.enqueueWork(applicationContext)

                System.gc()

                Log.d(LOG_TAG, "[onStop] finished")
            }
        })

        // API Providerの初期化
        providerApis.forEach { api ->
            api.onCreate(this)
        }

        // 画像キャッシュの初期化
        BitmapCache.initialize(this)

        if (CompatUtil.getProcessName() == packageName) {
            processLifecycleOwner.lifecycle.addObserver(streamManager)

            // Mastodon: default visibilityの取得
            for (user in accountManager.users) {
                if (user.Provider.apiType == Provider.API_MASTODON) {
                    FetchDefaultVisibilityTask(this, defaultVisibilityCache, user).executeParallel()
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            BitmapCache.release()
        }
    }

    override fun getProviderApi(userRecord: AuthUserRecord): ProviderApi? {
        val apiType = userRecord.Provider.apiType
        if (apiType in 0..providerApis.size) {
            return providerApis[apiType]
        }
        return null
    }

    override fun getProviderApi(apiType: Int): ProviderApi {
        if (apiType in 0..providerApis.size) {
            return providerApis[apiType]
        }
        throw UnsupportedOperationException("API Type $apiType not implemented.")
    }

    override fun getProviderStream(userRecord: AuthUserRecord): ProviderStream? = streamManager.getProviderStream(userRecord)

    override fun getProviderStream(apiType: Int): ProviderStream = streamManager.getProviderStream(apiType)

    override fun getProviderStreams(): Array<out ProviderStream?> = streamManager.providerStreams

    override fun startStreamChannels() = streamManager.startStreamChannels()

    private fun installSecurityProvider() {
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (ignore: GooglePlayServicesRepairableException) {
        } catch (ignore: GooglePlayServicesNotAvailableException) {
        }
    }

    private fun initOkHttpClient() {
        okhttpClient = OkHttpClient.Builder().addInterceptor(UserAgentInterceptor(this)).build()
        Media.setHttpClient(okhttpClient)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createCommonNotificationChannels(nm: NotificationManager) {
        val generalChannel = NotificationChannel(
                getString(R.string.notification_channel_id_general),
                "その他の通知",
                NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(generalChannel)

        val errorChannel = NotificationChannel(
                getString(R.string.notification_channel_id_error),
                "エラーの通知",
                NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(errorChannel)

        val coreServiceChannel = NotificationChannel(
                getString(R.string.notification_channel_id_core_service),
                "常駐サービス",
                NotificationManager.IMPORTANCE_MIN)
        nm.createNotificationChannel(coreServiceChannel)

        val asyncActionChannel = NotificationChannel(
                getString(R.string.notification_channel_id_async_action),
                "バックグラウンド処理",
                NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(asyncActionChannel)
    }

    /**
     * すべてのアカウント向けの共通通知チャンネルを生成します。
     * @param nm NotificationManager
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAllAccountNotificationChannels(nm: NotificationManager) {
        val channels: MutableList<NotificationChannel> = ArrayList()
        val audioAttributes = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_NOTIFICATION).build()
        val groupId = NotificationChannelPrefix.GROUP_ACCOUNT + "all"

        val group = NotificationChannelGroup(groupId, "すべてのアカウント")
        nm.createNotificationChannelGroup(group)

        // Mention
        val mentionChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + "all", "メンション通知", NotificationManager.IMPORTANCE_HIGH)
        mentionChannel.group = groupId
        mentionChannel.description = "@付き投稿の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(mentionChannel)

        // Repost (RT, Boost)
        val repostChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + "all", "ブースト通知", NotificationManager.IMPORTANCE_HIGH)
        repostChannel.group = groupId
        repostChannel.description = "あなたの投稿がブーストされた時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(repostChannel)

        // Favorite
        val favoriteChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + "all", "お気に入り通知", NotificationManager.IMPORTANCE_HIGH)
        favoriteChannel.group = groupId
        favoriteChannel.description = "あなたの投稿がお気に入り登録された時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(favoriteChannel)

        // Message
        val messageChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + "all", "メッセージ通知", NotificationManager.IMPORTANCE_HIGH)
        messageChannel.group = groupId
        messageChannel.description = "あなた宛のメッセージを受信した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(messageChannel)

        // Repost Respond (RT-Respond)
        val repostRespondChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + "all", "BTレスポンス通知", NotificationManager.IMPORTANCE_HIGH)
        repostRespondChannel.group = groupId
        repostRespondChannel.description = "あなたの投稿がブーストされ、その直後に感想文らしき投稿を発見した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(repostRespondChannel)

        nm.createNotificationChannels(channels)
    }

    /**
     * マイグレーションの必要な設定を検出し、修正します。
     */
    private fun migratePreference() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        // ver 3.0.0
        sp.edit {
            // 文字サイズの設定
            if (sp.contains("pref_font_timeline") && !sp.getBoolean("pref_font_timeline__migrate_3_0_0", false)) {
                val size = (sp.getString("pref_font_timeline", "14")!!.toInt() * 0.8).toInt()
                putString("pref_font_timeline", size.toString())
            }
            putBoolean("pref_font_timeline__migrate_3_0_0", true)

            // 入力文字サイズの設定
            if (sp.contains("pref_font_input") && !sp.getBoolean("pref_font_input__migrate_3_0_0", false)) {
                val size = (sp.getString("pref_font_input", "18")!!.toInt() * 0.8).toInt()
                putString("pref_font_input", size.toString())
            }
            putBoolean("pref_font_input__migrate_3_0_0", true)
        }
    }

    companion object {
        private const val LOG_TAG = "AppContext"

        @JvmStatic
        fun getInstance(context: Context) = context.applicationContext as App
    }
}