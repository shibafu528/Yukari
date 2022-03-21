package shibafu.yukari.twitter.entity

import org.eclipse.collections.impl.factory.primitive.LongLists
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.MediaFactory
import shibafu.yukari.media2.impl.TwitterVideo
import twitter4j.DirectMessage
import twitter4j.MediaEntity
import java.util.*

class TwitterMessage(val message: DirectMessage,
                     val sender: twitter4j.User,
                     val recipient: twitter4j.User,
                     override var representUser: AuthUserRecord) : Status {
    override val id: Long
        get() = message.id

    override val user: User = TwitterUser(sender)

    override val text: String
        get() = message.text

    override val recipientScreenName: String
        get() = recipient.screenName

    override val createdAt: Date
        get() = message.createdAt

    override val source: String
        get() = "DirectMessage"

    override val url: String = "https://twitter.com/${user.screenName}/direct_message/$id"

    override val mentions: List<Mention> = listOf(TwitterMention(recipient.id, recipient.screenName))

    override val media: List<Media>

    override val links: List<String>

    override val tags: List<String> = message.hashtagEntities.map { it.text }

    override var favoritesCount: Int = 0

    override var repostsCount: Int = 0

    override val metadata: StatusPreforms = StatusPreforms()

    override val providerApiType: Int = Provider.API_TWITTER

    override val providerHost: String = Provider.TWITTER.host

    override var representOverrode: Boolean = false

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    init {
        val media = LinkedHashSet<Media>()
        val links = LinkedHashSet<String>()
        message.urlEntities.forEach { entity ->
            if (entity.expandedURL.contains("twitter.com/messages/media/")) {
                return@forEach
            }

            val m = MediaFactory.newInstance(entity.expandedURL)
            if (m != null) {
                media += m
            } else {
                links += entity.expandedURL
            }
        }

        message.mediaEntities.forEach { entity ->
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
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TwitterMessage) return false

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

            if (userRecord.ScreenName == sender.screenName) {
                return Status.RELATION_OWNED
            }
        }
        return Status.RELATION_MENTIONED_TO_ME
    }
}