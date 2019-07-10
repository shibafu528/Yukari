package shibafu.yukari.twitter.entity

import android.util.Log
import org.eclipse.collections.api.list.primitive.LongList
import org.eclipse.collections.impl.factory.primitive.LongLists
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.MediaFactory
import shibafu.yukari.media2.impl.TwitterVideo
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.util.MorseCodec
import twitter4j.MediaEntity
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.LinkedHashSet

class TwitterStatus(val status: twitter4j.Status, override var representUser: AuthUserRecord) : Status {

    override val id: Long = status.id

    override val user: User = TwitterUser(status.user)

    override val text: String

    override val recipientScreenName: String = representUser.ScreenName

    override val createdAt: Date = status.createdAt

    override val source: String = status.source.let { source ->
        val matcher = PATTERN_VIA.matcher(source)
        if (matcher.find()) {
            matcher.group(1)
        } else {
            source
        }
    }

    override val isRepost: Boolean = status.isRetweet

    override val originStatus: Status = if (isRepost) TwitterStatus(status.retweetedStatus, representUser) else this

    override val url: String = "https://twitter.com/${user.screenName}/status/$id"

    override val inReplyToId: Long = status.inReplyToStatusId

    override val mentions: List<Mention> = status.userMentionEntities.map { TwitterMention(it) }

    override val media: List<Media>

    override val links: List<String>

    override val tags: List<String> = status.hashtagEntities.map { it.text }

    override var favoritesCount: Int = status.originStatus.favoriteCount

    override var repostsCount: Int = status.originStatus.retweetCount

    override val metadata: StatusPreforms = StatusPreforms()

    override val providerApiType: Int = Provider.API_TWITTER

    override val providerHost: String = Provider.TWITTER.host

    override var representOverrode: Boolean = false

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    val quoteEntities: LongList

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TwitterStatus) return false

        return this.id == other.id
    }

    override fun hashCode(): Int {
        return (this.id xor (this.id ushr 32)).toInt()
    }

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
            if (userRecord.Provider.apiType != providerApiType) {
                return@forEach
            }

            status.userMentionEntities.forEach { entity ->
                if (userRecord.ScreenName == entity.screenName) {
                    return Status.RELATION_MENTIONED_TO_ME
                }
            }
            if (userRecord.ScreenName == status.inReplyToScreenName) {
                return Status.RELATION_MENTIONED_TO_ME
            }
            if (userRecord.ScreenName == user.screenName) {
                return Status.RELATION_OWNED
            }
        }
        return Status.RELATION_NONE
    }

    override fun canRepost(userRecord: AuthUserRecord): Boolean {
        return userRecord.Provider.apiType == Provider.API_TWITTER &&
                (userRecord.ScreenName == originStatus.user.screenName || !originStatus.user.isProtected)
    }

    override fun canFavorite(userRecord: AuthUserRecord): Boolean {
        return userRecord.Provider.apiType == Provider.API_TWITTER
    }

    private val twitter4j.Status.originStatus: twitter4j.Status
        get() = if (this.isRetweet) this.retweetedStatus else this

    init {
        // PreformedStatusではこの先の処理のように加工した値が取れてしまうため、ラップされている内部のStatusを参照する。
        // 互換性が不要であればinit内では単に this#status を参照して良い。
        val status = if (status is PreformedStatus) {
            Log.w("TwitterStatus", "PreformedStatus wrapped. Should use TwitterStatus directly.", Throwable())
            status.baseStatus
        } else {
            status
        }

        if (isRepost) {
            text = originStatus.text
            media = originStatus.media
            links = originStatus.links
            quoteEntities = (originStatus as TwitterStatus).quoteEntities
        } else {
            text = status.originStatus.text.let {
                var text = it

                // 全Entityの置換
                status.urlEntities.forEach { entity ->
                    text = text.replace(entity.url, entity.expandedURL)
                }
                status.mediaEntities.forEach { entity ->
                    text = text.replace(entity.url, entity.mediaURLHttps)
                }

                // モールスの復号
                text = MorseCodec.decode(text)

                text
            }

            val media = LinkedHashSet<Media>()
            val links = LinkedHashSet<String>()
            val quotes = LongLists.mutable.empty()
            status.urlEntities.forEach { entity ->
                val m = MediaFactory.newInstance(entity.expandedURL)
                if (m != null) {
                    media += m
                } else {
                    links += entity.expandedURL
                }

                val matcher = PATTERN_STATUS.matcher(entity.expandedURL)
                if (matcher.find()) {
                    matcher.group(1).toLongOrNull()?.let(quotes::add)
                }
            }
            if (status.quotedStatusPermalink != null) {
                links += status.quotedStatusPermalink.expandedURL
            }
            if (status.quotedStatusId > -1 && !quotes.contains(status.quotedStatusId)) {
                quotes.add(status.quotedStatusId)
            }

            status.mediaEntities.forEach { entity ->
                when (entity.type) {
                    "video", "animated_gif" -> {
                        if (entity.videoVariants.isNotEmpty()) {
                            var removedExistsUrl = false

                            var largest: MediaEntity.Variant? = null
                            for (variant in entity.videoVariants) {
                                if (!variant.contentType.startsWith("video/")) continue

                                if (largest == null || largest.bitrate < variant.bitrate) {
                                    largest = variant
                                }
                                if (!removedExistsUrl) {
                                    val iterator = media.iterator()
                                    while (iterator.hasNext()) {
                                        if (iterator.next().browseUrl == entity.mediaURLHttps) {
                                            iterator.remove()
                                        }
                                    }

                                    removedExistsUrl = true
                                }
                            }
                            if (largest != null) {
                                media += TwitterVideo(largest.url, entity.mediaURLHttps)
                            }
                        }
                    }
                    else -> {
                        media += MediaFactory.newInstance(entity.mediaURLHttps)
                    }
                }
            }

            this.media = media.toList()
            this.links = links.toList()
            this.quoteEntities = quotes.distinct()
        }
    }

    companion object {
        private val PATTERN_VIA = Pattern.compile("<a .*>(.+)</a>")
        private val PATTERN_STATUS = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)\$")
    }
}
