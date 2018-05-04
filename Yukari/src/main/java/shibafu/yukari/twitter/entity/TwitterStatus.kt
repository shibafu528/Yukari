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
import twitter4j.MediaEntity
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.LinkedHashSet

class TwitterStatus(val status: twitter4j.Status, override var representUser: AuthUserRecord) : Status {

    override val id: Long = status.id

    override val user: User = TwitterUser(status.user)

    override val text: String = status.originStatus.text.let {
        var text = it

        // 全Entityの置換
        status.urlEntities.forEach { entity ->
            text = text.replace(entity.url, entity.expandedURL)
        }
        status.mediaEntities.forEach { entity ->
            text = text.replace(entity.url, entity.mediaURLHttps)
        }

        text
    }

    override val recipientScreenName: String = if (status is PreformedStatus) status.representUser.ScreenName else ""

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

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    val quoteEntities: LongList

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
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

    private val twitter4j.Status.originStatus: twitter4j.Status
        get() = if (this.isRetweet) this.retweetedStatus else this

    init {
        if (status is PreformedStatus) {
            Log.w("TwitterStatus", "PreformedStatus wrapped. Should use TwitterStatus directly.", Throwable())
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
        this.quoteEntities = quotes
    }

    companion object {
        private val PATTERN_VIA = Pattern.compile("<a .*>(.+)</a>")
        private val PATTERN_STATUS = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)\$")
    }
}
