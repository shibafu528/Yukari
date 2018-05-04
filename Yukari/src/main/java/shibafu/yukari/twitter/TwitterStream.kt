package shibafu.yukari.twitter

import android.util.Log
import shibafu.yukari.database.Provider
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.streaming.Stream
import shibafu.yukari.twitter.streaming.StreamListener
import shibafu.yukari.twitter.streaming.StreamUser
import shibafu.yukari.util.StringUtil
import twitter4j.DirectMessage
import twitter4j.Status
import twitter4j.StatusDeletionNotice
import twitter4j.User

class TwitterStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        this.service = service

        service.users.forEach { user ->
            if (user.Provider.apiType == Provider.API_TWITTER) {
                val ch = addUser(user)
                if (user.isActive) {
                    ch.forEach(StreamChannel::start)
                }
            }
        }
    }

    override fun onDestroy() {
        channels.forEach(StreamChannel::stop)
        channels = emptyList()
    }

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> {
        val ch = listOf(UserStreamChannel(service, userRecord))
        channels += ch
        return ch
    }

    override fun removeUser(userRecord: AuthUserRecord) {
        val ch = channels.filter { it.userRecord == userRecord }
        ch.forEach(StreamChannel::stop)
        channels -= ch
    }
}

class UserStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val allowUserControl: Boolean = true
    private val stream: StreamUser = StreamUser(service.applicationContext, userRecord)

    init {
        stream.listener = UserStreamListener(service.timelineHub)
    }

    override fun start() {
        stream.start()
    }

    override fun stop() {
        stream.stop()
    }

    private class UserStreamListener(private val hub: TimelineHub) : StreamListener {
        override fun onFavorite(from: Stream, user: User, user2: User, status: Status) {
            if (PUT_STREAM_LOG) Log.d("onFavorite", String.format("f:%s s:%d", from.userRecord.ScreenName, status.id))

            val preformedStatus = PreformedStatus(status, from.userRecord)
            val twitterStatus = TwitterStatus(preformedStatus, from.userRecord)
            val twitterUser = TwitterUser(user)

            hub.onFavorite(twitterUser, twitterStatus)
        }

        override fun onUnfavorite(from: Stream, user: User, user2: User, status: Status) {
            if (PUT_STREAM_LOG) Log.d("onUnfavorite", String.format("f:%s s:%s", from.userRecord.ScreenName, status.text))

            val preformedStatus = PreformedStatus(status, from.userRecord)
            val twitterStatus = TwitterStatus(preformedStatus, from.userRecord)
            val twitterUser = TwitterUser(user)

            hub.onUnfavorite(twitterUser, twitterStatus)
        }

        override fun onFollow(from: Stream, user: User, user2: User) {

        }

        override fun onDirectMessage(from: Stream, directMessage: DirectMessage) {
            hub.onDirectMessage("Twitter.StreamManager", TwitterMessage(directMessage, from.userRecord), true)
        }

        override fun onBlock(from: Stream, user: User, user2: User) {

        }

        override fun onUnblock(from: Stream, user: User, user2: User) {

        }

        override fun onStatus(from: Stream, status: Status) {
            if (PUT_STREAM_LOG) {
                Log.d(LOG_TAG,
                        String.format("onStatus: %s from %s [%s:%s]:@%s %s",
                                StringUtil.formatDate(status.createdAt),
                                from.userRecord.ScreenName,
                                from.javaClass.simpleName,
                                status.javaClass.simpleName,
                                status.user.screenName,
                                status.text))
            }

            hub.onStatus("TwitterStream.UserStreamChannel", TwitterStatus(status, from.userRecord), true)
        }

        override fun onDelete(from: Stream, statusDeletionNotice: StatusDeletionNotice) {
            hub.onDelete(TwitterStatus::class.java, statusDeletionNotice.statusId)
        }

        override fun onDeletionNotice(from: Stream, directMessageId: Long, userId: Long) {
            hub.onDelete(TwitterMessage::class.java, directMessageId)
        }
    }

    companion object {
        private const val LOG_TAG = "UserStreamChannel"
        private const val PUT_STREAM_LOG = false
    }
}
