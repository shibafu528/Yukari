package shibafu.yukari.twitter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import shibafu.yukari.database.Provider
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.streaming.FilterStream
import shibafu.yukari.twitter.streaming.Stream
import shibafu.yukari.twitter.streaming.StreamUser
import shibafu.yukari.util.StringUtil
import twitter4j.DirectMessage
import twitter4j.Status
import twitter4j.StatusDeletionNotice
import twitter4j.User

private const val PUT_STREAM_LOG = false

class TwitterStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        Log.d(LOG_TAG, "onCreate")

        this.service = service

        service.users.forEach { user ->
            if (user.Provider.apiType == Provider.API_TWITTER) {
                addUser(user)
            }
        }
    }

    override fun onStart() {
        Log.d(LOG_TAG, "onStart")

        channels.forEach { ch ->
            if (ch is UserStreamChannel && !ch.isRunning && ch.userRecord.isActive) {
                ch.start()
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

        val ch = listOf(UserStreamChannel(service, userRecord))
        channels += ch
        return ch
    }

    override fun removeUser(userRecord: AuthUserRecord) {
        Log.d(LOG_TAG, "Remove user: @${userRecord.ScreenName}")

        val ch = channels.filter { it.userRecord == userRecord }
        ch.forEach(StreamChannel::stop)
        channels -= ch
    }

    fun startFilterStream(query: String, userRecord: AuthUserRecord) {
        val ch = channels.firstOrNull { it.userRecord == userRecord && it is FilterStreamChannel } as? FilterStreamChannel
                ?: FilterStreamChannel(service, userRecord).apply { channels += this }
        ch.addQuery(query)
        if (!ch.isRunning) {
            ch.start()
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Start FilterStream:$query", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopFilterStream(query: String, userRecord: AuthUserRecord) {
        val ch = channels.firstOrNull { it.userRecord == userRecord && it is FilterStreamChannel } as? FilterStreamChannel
        if (ch != null) {
            ch.removeQuery(query)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "Stop FilterStream:$query", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val LOG_TAG = "TwitterStream"
    }
}

private class UserStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "UserStream"
    override val channelName: String = "UserStream"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val stream: StreamUser = StreamUser(service.applicationContext, userRecord)

    init {
        stream.listener = StreamListener("TwitterStream.UserStreamChannel", service.timelineHub)
    }

    override fun start() {
        stream.start()
        isRunning = true
    }

    override fun stop() {
        stream.stop()
        isRunning = false
    }
}

private class FilterStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "FilterStream"
    override val channelName: String = "FilterStream"
    override val allowUserControl: Boolean = false
    override var isRunning: Boolean = false
        private set

    val queryCount: Int
        get() = stream.queryCount

    private val stream: FilterStream = FilterStream.getInstance(service, userRecord)

    init {
        stream.listener = StreamListener("TwitterStream.FilterStreamChannel", service.timelineHub)
    }

    override fun start() {
        stream.start()
        isRunning = true
    }

    override fun stop() {
        stream.stop()
        isRunning = false
    }

    fun addQuery(query: String) {
        stream.addQuery(query)
        if (isRunning) {
            stream.stop()
            stream.start()
        }
    }

    fun removeQuery(query: String) {
        stream.removeQuery(query)
        if (isRunning) {
            stream.stop()
            // まだクエリが残っていれば再起動
            if (0 < stream.queryCount) {
                stream.start()
            } else {
                isRunning = false
            }
        }
    }
}

private class StreamListener(private val timelineId: String, private val hub: TimelineHub) : shibafu.yukari.twitter.streaming.StreamListener {
    override fun onFavorite(from: Stream, user: User, user2: User, status: Status) {
        if (PUT_STREAM_LOG) Log.d(LOG_TAG, String.format("[%s] onFavorite: f:%s s:%d", timelineId, from.userRecord.ScreenName, status.id))

        val preformedStatus = PreformedStatus(status, from.userRecord)
        val twitterStatus = TwitterStatus(preformedStatus, from.userRecord)
        val twitterUser = TwitterUser(user)

        hub.onFavorite(twitterUser, twitterStatus)
    }

    override fun onUnfavorite(from: Stream, user: User, user2: User, status: Status) {
        if (PUT_STREAM_LOG) Log.d(LOG_TAG, String.format("[%s] onUnfavorite: f:%s s:%s", timelineId, from.userRecord.ScreenName, status.text))

        val preformedStatus = PreformedStatus(status, from.userRecord)
        val twitterStatus = TwitterStatus(preformedStatus, from.userRecord)
        val twitterUser = TwitterUser(user)

        hub.onUnfavorite(twitterUser, twitterStatus)
    }

    override fun onFollow(from: Stream, user: User, user2: User) {

    }

    override fun onDirectMessage(from: Stream, directMessage: DirectMessage) {
        hub.onDirectMessage(timelineId, TwitterMessage(directMessage, from.userRecord), true)
    }

    override fun onBlock(from: Stream, user: User, user2: User) {

    }

    override fun onUnblock(from: Stream, user: User, user2: User) {

    }

    override fun onStatus(from: Stream, status: Status) {
        if (PUT_STREAM_LOG) {
            Log.d(LOG_TAG,
                    String.format("[%s] onStatus: %s from %s [%s:%s]:@%s %s",
                            timelineId,
                            StringUtil.formatDate(status.createdAt),
                            from.userRecord.ScreenName,
                            from.javaClass.simpleName,
                            status.javaClass.simpleName,
                            status.user.screenName,
                            status.text))
        }

        hub.onStatus(timelineId, TwitterStatus(status, from.userRecord), true)
    }

    override fun onDelete(from: Stream, statusDeletionNotice: StatusDeletionNotice) {
        hub.onDelete(TwitterStatus::class.java, statusDeletionNotice.statusId)
    }

    override fun onDeletionNotice(from: Stream, directMessageId: Long, userId: Long) {
        hub.onDelete(TwitterMessage::class.java, directMessageId)
    }

    companion object {
        private const val LOG_TAG = "StreamListener"
    }
}