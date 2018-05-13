package shibafu.yukari.mastodon

import android.util.Log
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Handler
import com.sys1yagi.mastodon4j.api.Shutdownable
import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Streaming
import kotlinx.coroutines.experimental.launch
import shibafu.yukari.database.Provider
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.mastodon.entity.DonUser
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord

class MastodonStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        Log.d(LOG_TAG, "onCreate")

        this.service = service

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

        channels.forEach(StreamChannel::stop)
        channels = emptyList()
    }

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> {
        Log.d(LOG_TAG, "Add user: @${userRecord.ScreenName}")

        if (channels.any { it.userRecord == userRecord }) {
            Log.d(LOG_TAG, "@${userRecord.ScreenName} is already added.")
            return emptyList()
        }

        val ch = listOf(
                UserStreamChannel(service, userRecord),
                LocalStreamChannel(service, userRecord),
                PublicStreamChannel(service, userRecord)
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

    companion object {
        private const val LOG_TAG = "MastodonStream"
    }
}

private class UserStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/user"
    override val channelName: String = "Home and Notifications (/user)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler("MastodonStream.UserStreamChannel", service.timelineHub, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            try {
                shutdownable = streaming.user(handler)
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                isRunning = false
            }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class PublicStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/public"
    override val channelName: String = "Federated (/public)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler("MastodonStream.PublicStreamChannel", service.timelineHub, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            try {
                shutdownable = streaming.federatedPublic(handler)
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                isRunning = false
            }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class LocalStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/public/local"
    override val channelName: String = "Local (/public/local)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler("MastodonStream.LocalStreamChannel", service.timelineHub, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            try {
                shutdownable = streaming.localPublic(handler)
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                isRunning = false
            }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class StreamHandler(private val timelineId: String, private val hub: TimelineHub, private val userRecord: AuthUserRecord) : Handler {
    override fun onDelete(id: Long) {
        // TODO: ところでこれ、IDがインスタンスレベルでしか一意じゃない場合関係ないやつ消しとばすんですけど
        hub.onDelete(DonStatus::class.java, id)
    }

    override fun onNotification(notification: Notification) {
        val status = notification.status
        when (notification.type) {
            "mention" ->
                if (status != null) {
                    hub.onStatus(timelineId, DonStatus(status, userRecord), true)
                }
            "reblog" ->
                if (status != null) {
                    hub.onNotify(NotifyHistory.KIND_RETWEETED, DonUser(notification.account), DonStatus(status, userRecord))
                }
            "favourite" ->
                if (status != null) {
                    hub.onFavorite(DonUser(notification.account), DonStatus(status, userRecord))
                }
            "follow" -> {}
        }
    }

    override fun onStatus(status: Status) {
        hub.onStatus(timelineId, DonStatus(status, userRecord), true)
    }
}