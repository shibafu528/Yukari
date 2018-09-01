package shibafu.yukari.twitter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.experimental.launch
import shibafu.yukari.database.Provider
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.twitter.streaming.AutoReloadStream
import shibafu.yukari.twitter.streaming.FilterStream
import shibafu.yukari.twitter.streaming.Stream
import shibafu.yukari.util.StringUtil
import twitter4j.DirectMessage
import twitter4j.Status
import twitter4j.StatusDeletionNotice
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.User

private const val PUT_STREAM_LOG = false

class TwitterStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService
    private lateinit var filterStreamListener: FilterStreamListener

    override fun onCreate(service: TwitterService) {
        Log.d(LOG_TAG, "onCreate")

        this.service = service
        this.filterStreamListener = FilterStreamListener(service.timelineHub)

        service.users.forEach { user ->
            if (user.Provider.apiType == Provider.API_TWITTER) {
                addUser(user)
            }
        }
    }

    override fun onStart() {
        Log.d(LOG_TAG, "onStart")

        val channelStates = service.database.getRecords(StreamChannelState::class.java)
        channels.forEach { ch ->
            if (ch is AutoReloadChannel || ch is FollowedStreamChannel) {
                if (!ch.isRunning) {
                    val state = channelStates.firstOrNull { it.accountId == ch.userRecord.InternalId && it.channelId == ch.channelId }
                    if (state != null && state.isActive) {
                        ch.start()
                    }
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

        FilterStream.getInstance(service, userRecord).listener = filterStreamListener

        val ch = listOf(AutoReloadChannel(service, userRecord), FilterStreamChannel(service, userRecord), FollowedStreamChannel(service, userRecord))
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
        val ch = channels.first { it.userRecord == userRecord && it is FilterStreamChannel } as FilterStreamChannel
        ch.addQuery(query)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Start FilterStream:$query", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopFilterStream(query: String, userRecord: AuthUserRecord) {
        val ch = channels.first { it.userRecord == userRecord && it is FilterStreamChannel } as FilterStreamChannel
        ch.removeQuery(query)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Stop FilterStream:$query", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LOG_TAG = "TwitterStream"
    }
}

private class FilterStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "FilterStream"
    override val channelName: String = "FilterStream"
    override val allowUserControl: Boolean = false
    override val isRunning: Boolean
        get() = stream.isRunning

    private val stream: FilterStream = FilterStream.getInstance(service, userRecord)

    override fun start() {
        // FilterStream内で自動管理
    }

    override fun stop() {
        // FilterStream内で自動管理
    }

    fun addQuery(query: String) {
        stream.addQuery(query)
    }

    fun removeQuery(query: String) {
        stream.removeQuery(query)
    }
}

private class AutoReloadChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "AutoReload"
    override val channelName: String = "AutoReload (Home & Mentions)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val stream: AutoReloadStream = AutoReloadStream(service.applicationContext, userRecord)

    init {
        stream.listener = StreamListener("TwitterStream.AutoReloadChannel",
                service.timelineHub, service.getProviderApi(userRecord) as TwitterApi)
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

private class FollowedStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "FollowedStream"
    override val channelName: String = "FollowedStream"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val stream: FilterStream = FilterStream.getInstance(service, userRecord)

    override fun start() {
        launch {
            Log.d("FollowedStreamChannel", "Initializing FollowedStreamChannel... user: @${userRecord.ScreenName}")

            val api = service.getProviderApi(userRecord)?.getApiClient(userRecord) as? Twitter
            if (api == null) {
                Log.e("FollowedStreamChannel", "Failed to initialize FollowedStreamChannel user: @${userRecord.ScreenName}")
                isRunning = false
                return@launch
            }

            try {
                val friendIds = api.getFriendsIDs(userRecord.NumericId, -1, 5000)
                Log.d("FollowedStreamChannel", "Collect ${friendIds.iDs.size} IDs user: @${userRecord.ScreenName}")
                stream.addFollowIds(*friendIds.iDs)
            } catch (e: TwitterException) {
                e.printStackTrace()
                isRunning = false
                return@launch
            }

            Log.d("FollowedStreamChannel", "Start FollowedStreamChannel user: @${userRecord.ScreenName}")
        }
        isRunning = true
    }

    override fun stop() {
        stream.clearFollowId()
        isRunning = false
    }
}

private class StreamListener(private val timelineId: String,
                             private val hub: TimelineHub,
                             private val api: TwitterApi) : shibafu.yukari.twitter.streaming.StreamListener {
    override fun onFavorite(from: Stream, user: User, user2: User, status: Status) {
        if (PUT_STREAM_LOG) Log.d(LOG_TAG, String.format("[%s] onFavorite: f:%s s:%d", timelineId, from.userRecord.ScreenName, status.id))

        val twitterStatus = TwitterStatus(status, from.userRecord)
        val twitterUser = TwitterUser(user)

        hub.onFavorite(twitterUser, twitterStatus)
    }

    override fun onUnfavorite(from: Stream, user: User, user2: User, status: Status) {
        if (PUT_STREAM_LOG) Log.d(LOG_TAG, String.format("[%s] onUnfavorite: f:%s s:%s", timelineId, from.userRecord.ScreenName, status.text))

        val twitterStatus = TwitterStatus(status, from.userRecord)
        val twitterUser = TwitterUser(user)

        hub.onUnfavorite(twitterUser, twitterStatus)
    }

    override fun onFollow(from: Stream, user: User, user2: User) {

    }

    override fun onDirectMessage(from: Stream, directMessage: DirectMessage) {
        try {
            val twitter = api.getApiClient(from.userRecord) as Twitter
            val users = twitter.lookupUsers(directMessage.recipientId, directMessage.senderId)
            val message = TwitterMessage(directMessage,
                    users.first { it.id == directMessage.senderId },
                    users.first { it.id == directMessage.recipientId },
                    from.userRecord)

            hub.onDirectMessage(timelineId, message, true)
        } catch (e: TwitterException) {
            e.printStackTrace()
        }
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

private class FilterStreamListener(private val hub: TimelineHub) : shibafu.yukari.twitter.streaming.StreamListener {
    override fun onFavorite(from: Stream, user: User, user2: User, status: Status) {}

    override fun onUnfavorite(from: Stream, user: User, user2: User, status: Status) {}

    override fun onFollow(from: Stream, user: User, user2: User) {}

    override fun onDirectMessage(from: Stream, directMessage: DirectMessage) {}

    override fun onBlock(from: Stream, user: User, user2: User) {}

    override fun onUnblock(from: Stream, user: User, user2: User) {}

    override fun onStatus(from: Stream, status: Status) {
        val filterStream = from as FilterStream
        val text = if (status.isRetweet) status.retweetedStatus.text else status.text

        if (filterStream.followIds.contains(status.user.id)) {
            // follow着信
            if (status.userMentionEntities.isNotEmpty() && status.text.startsWith("@")) {
                // メンションから始まるツイートの場合、対象に自分かフォロイーが含まれている場合のみ配信
                if (status.userMentionEntities.any { it.id == from.userRecord.NumericId || filterStream.followIds.contains(it.id) }) {
                    hub.onStatus(TIMELINE_ID, TwitterStatus(status, from.userRecord), true)
                }
            } else {
                // 通常ツイート
                hub.onStatus(TIMELINE_ID, TwitterStatus(status, from.userRecord), true)
            }
        } else if (filterStream.queries.any { q -> text.contains(q.validQuery) }) {
            // track着信
            hub.onStatus(TIMELINE_ID, TwitterStatus(status, from.userRecord), true)
        }
    }

    override fun onDelete(from: Stream, statusDeletionNotice: StatusDeletionNotice) {
        hub.onDelete(TwitterStatus::class.java, statusDeletionNotice.statusId)
    }

    override fun onDeletionNotice(from: Stream, directMessageId: Long, userId: Long) {}

    companion object {
        private const val TIMELINE_ID = "TwitterStream.FilterStream"
    }
}