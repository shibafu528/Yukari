package shibafu.yukari.twitter.entity

import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import java.util.*

class TwitterStatus(val status: twitter4j.Status, override var representUser: AuthUserRecord) : Status {
    override val id: Long
        get() = status.id

    override val user: User by lazy { TwitterUser(status.originStatus.user) }

    override val text: String
        get() = status.originStatus.text

    override val recipientScreenName: String
        get() = if (status is PreformedStatus) status.representUser.ScreenName else ""

    override val createdAt: Date
        get() = status.createdAt

    override val source: String
        get() = status.source

    override val isRepost: Boolean
        get() = status.isRetweet

    override val originStatus: Status = if (isRepost) TwitterStatus(status.retweetedStatus, representUser) else this

    override val metadata: StatusPreforms = StatusPreforms()

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Long {
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
}
