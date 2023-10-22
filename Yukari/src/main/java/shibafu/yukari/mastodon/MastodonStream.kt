package shibafu.yukari.mastodon

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import info.shibafu528.yukari.api.mastodon.ws.StreamListener
import info.shibafu528.yukari.api.mastodon.ws.Subscription
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Response
import shibafu.yukari.common.okhttp.UserAgentInterceptor
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.Provider
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.mastodon.entity.DonNotification
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.mastodon.streaming.StreamClientManager
import shibafu.yukari.service.TwitterService
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.putDebugLog

class MastodonStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService
    private lateinit var streamClientManager: StreamClientManager

    override fun onCreate(service: TwitterService) {
        Log.d(LOG_TAG, "onCreate")

        this.service = service

        val enforceLegacy = service.defaultSharedPreferences.getBoolean("pref_mastodon_enforce_legacy_stream_client", false)
        this.streamClientManager = StreamClientManager(OkHttpClient.Builder().addInterceptor(UserAgentInterceptor(service.applicationContext)), enforceLegacy)

        service.users.forEach { user ->
            if (user.Provider.apiType == Provider.API_MASTODON) {
                addUser(user)
            }
        }
    }

    override fun onStart() {
        Log.d(LOG_TAG, "onStart")

        val channelStates = service.database.getRecords(StreamChannelState::class.java)
        channels.forEach { ch ->
            if (!ch.isRunning) {
                val state = channelStates.firstOrNull { it.accountId == ch.userRecord.InternalId && it.channelId == ch.channelId }
                if (state != null && state.isActive) {
                    ch.start()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "onDestroy")

        channels.parallelStream().forEach(StreamChannel::stop)
        channels = emptyList()
    }

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> {
        Log.d(LOG_TAG, "Add user: @${userRecord.ScreenName}")

        if (channels.any { it.userRecord == userRecord }) {
            Log.d(LOG_TAG, "@${userRecord.ScreenName} is already added.")
            return emptyList()
        }

        val ch = listOf(
                UserStreamChannel(service, userRecord, streamClientManager),
                LocalStreamChannel(service, userRecord, streamClientManager),
                PublicStreamChannel(service, userRecord, streamClientManager)
        )
        channels += ch
        return ch
    }

    override fun removeUser(userRecord: AuthUserRecord) {
        Log.d(LOG_TAG, "Remove user: @${userRecord.ScreenName}")

        val ch = channels.filter { it.userRecord == userRecord }
        ch.forEach(StreamChannel::stop)
        channels -= ch
    }

    fun isConnectedHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope): Boolean {
        return channels.any { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }
    }

    fun startHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope) {
        if (channels.any { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }) {
            return
        }

        val ch = HashTagStreamChannel(service, userRecord, streamClientManager, tag, scope)
        ch.start()
        channels += ch

        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Start HashTagStream:$tag", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope) {
        val ch = channels.first { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }
        ch.stop()
        channels -= ch

        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Stop HashTagStream:$tag", Toast.LENGTH_SHORT).show()
        }
    }

    enum class Scope(val channelId: String, val channelName: String) {
        FEDERATED("/hashtag", "HashTag"),
        LOCAL("/hashtag/local", "Local HashTag")
    }

    companion object {
        private const val LOG_TAG = "MastodonStream"

        const val USER_STREAM_ID = "MastodonStream.UserStreamChannel"
        const val PUBLIC_STREAM_ID = "MastodonStream.PublicStreamChannel"
        const val LOCAL_STREAM_ID = "MastodonStream.LocalStreamChannel"
        const val HASHTAG_STREAM_ID = "MastodonStream.HashTagStreamChannel"
    }
}

private class UserStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord, private val streamClientManager: StreamClientManager) : StreamChannel {
    override val channelId: String = "/user"
    override val channelName: String = "ホームTLと通知 (/user)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private var clientRef: StreamClientManager.Ref? = null
    private val listener: StreamListener = Listener(MastodonStream.USER_STREAM_ID, channelId, service.applicationContext, service.database, service.timelineHub, userRecord)
    private val subscription = Subscription("user", listener)

    override fun start() {
        GlobalScope.launch {
            Log.d(UserStreamChannel::class.java.simpleName, "${MastodonStream.USER_STREAM_ID}@${userRecord.ScreenName}: Start subscribe.")
            clientRef = streamClientManager.take(userRecord).also { ref ->
                ref.client.subscribe(subscription)
            }
        }
        isRunning = true
    }

    override fun stop() {
        isRunning = false
        val ref = clientRef ?: return
        ref.client.unsubscribe(subscription)
        streamClientManager.release(ref)
    }
}

private class PublicStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord, private val streamClientManager: StreamClientManager) : StreamChannel {
    override val channelId: String = "/public"
    override val channelName: String = "連合TL (/public)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private var clientRef: StreamClientManager.Ref? = null
    private val listener: StreamListener = Listener(MastodonStream.PUBLIC_STREAM_ID, channelId, service.applicationContext, service.database, service.timelineHub, userRecord)
    private val subscription = Subscription("public", listener)

    override fun start() {
        GlobalScope.launch {
            Log.d(PublicStreamChannel::class.java.simpleName, "${MastodonStream.PUBLIC_STREAM_ID}@${userRecord.ScreenName}: Start subscribe.")
            clientRef = streamClientManager.take(userRecord).also { ref ->
                ref.client.subscribe(subscription)
            }
        }
        isRunning = true
    }

    override fun stop() {
        isRunning = false
        val ref = clientRef ?: return
        ref.client.unsubscribe(subscription)
        streamClientManager.release(ref)
    }
}

private class LocalStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord, private val streamClientManager: StreamClientManager) : StreamChannel {
    override val channelId: String = "/public/local"
    override val channelName: String = "ローカルTL (/public/local)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private var clientRef: StreamClientManager.Ref? = null
    private val listener: StreamListener = Listener(MastodonStream.LOCAL_STREAM_ID, channelId, service.applicationContext, service.database, service.timelineHub, userRecord)
    private val subscription = Subscription("public:local", listener)

    override fun start() {
        GlobalScope.launch {
            Log.d(LocalStreamChannel::class.java.simpleName, "${MastodonStream.LOCAL_STREAM_ID}@${userRecord.ScreenName}: Start subscribe.")
            clientRef = streamClientManager.take(userRecord).also { ref ->
                ref.client.subscribe(subscription)
            }
        }
        isRunning = true
    }

    override fun stop() {
        isRunning = false
        val ref = clientRef ?: return
        ref.client.unsubscribe(subscription)
        streamClientManager.release(ref)
    }
}

private class HashTagStreamChannel(private val service: TwitterService,
                                   override val userRecord: AuthUserRecord,
                                   private val streamClientManager: StreamClientManager,
                                   val tag: String,
                                   val scope: MastodonStream.Scope) : StreamChannel {
    override val channelId: String = "${scope.channelId}tag=$tag"
    override val channelName: String = "${scope.channelName} #$tag"
    override val allowUserControl: Boolean = false
    override var isRunning: Boolean = false
        private set

    private var clientRef: StreamClientManager.Ref? = null
    private val listener: StreamListener = Listener(MastodonStream.HASHTAG_STREAM_ID, channelId, service.applicationContext, service.database, service.timelineHub, userRecord)
    private val subscription = when (scope) {
        MastodonStream.Scope.FEDERATED -> Subscription("hashtag", listener, tag = tag)
        MastodonStream.Scope.LOCAL -> Subscription("hashtag:local", listener, tag = tag)
    }

    override fun start() {
        GlobalScope.launch {
            Log.d(HashTagStreamChannel::class.java.simpleName, "${MastodonStream.HASHTAG_STREAM_ID}@${userRecord.ScreenName}: Start subscribe.")

            clientRef = streamClientManager.take(userRecord).also { ref ->
                ref.client.subscribe(subscription)
            }
        }
        isRunning = true
    }

    override fun stop() {
        isRunning = false
        val ref = clientRef ?: return
        ref.client.unsubscribe(subscription)
        streamClientManager.release(ref)
    }
}

private class Listener(private val timelineId: String,
                       private val channelId: String,
                       private val context: Context,
                       private val database: CentralDatabase,
                       private val hub: TimelineHub,
                       private val userRecord: AuthUserRecord) : StreamListener {
    private val displayTimelineId = timelineId.replace("MastodonStream.", "").replace("Channel", "")

    override fun onUpdate(status: Status) {
        hub.onStatus(timelineId, DonStatus(status, userRecord), true)

        val account = status.account
        if (account != null && account.url == userRecord.Url) {
            // プロフィール情報の更新
            database.updateAccountProfile(userRecord.Provider.id,
                account.id,
                MastodonUtil.expandFullScreenName(account.acct, account.url),
                account.displayName.takeIf { !it.isEmpty() } ?: account.userName,
                account.avatar)
        }
    }

    override fun onNotification(notification: Notification) {
        hub.onNotification(timelineId, DonNotification(notification, userRecord))
    }

    override fun onDelete(id: Long) {
        hub.onDelete(userRecord.Provider.host, id)
    }

    override fun onOpen() {
        putDebugLog("$timelineId@${userRecord.ScreenName}: Stream connected.")
        context.sendBroadcast(Intent().apply {
            action = TwitterService.ACTION_STREAM_CONNECTED
            putExtra(TwitterService.EXTRA_USER, userRecord)
            putExtra(TwitterService.EXTRA_CHANNEL_ID, channelId)
            putExtra(TwitterService.EXTRA_CHANNEL_NAME, displayTimelineId)
        })
    }

    override fun onClosed() {
        putDebugLog("$timelineId@${userRecord.ScreenName}: Stream closed.")
        context.sendBroadcast(Intent().apply {
            action = TwitterService.ACTION_STREAM_DISCONNECTED
            putExtra(TwitterService.EXTRA_USER, userRecord)
            putExtra(TwitterService.EXTRA_CHANNEL_ID, channelId)
            putExtra(TwitterService.EXTRA_CHANNEL_NAME, displayTimelineId)
        })
    }

    override fun onFailure(t: Throwable, response: Response?) {
        putDebugLog("$timelineId@${userRecord.ScreenName}: Stream disconnected with error.")
        context.sendBroadcast(Intent().apply {
            action = TwitterService.ACTION_STREAM_DISCONNECTED
            putExtra(TwitterService.EXTRA_USER, userRecord)
            putExtra(TwitterService.EXTRA_CHANNEL_ID, channelId)
            putExtra(TwitterService.EXTRA_CHANNEL_NAME, displayTimelineId)
        })
    }
}
