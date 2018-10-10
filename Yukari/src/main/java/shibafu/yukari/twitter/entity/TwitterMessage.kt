package shibafu.yukari.twitter.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.DirectMessage
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

    override var favoritesCount: Int = 0

    override var repostsCount: Int = 0

    override val metadata: StatusPreforms = StatusPreforms()

    override val providerApiType: Int = Provider.API_TWITTER

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

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