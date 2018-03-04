package shibafu.yukari.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*

/**
 * 通知履歴
 */
class NotifyHistory(timeAtMillis: Long, @NotifyKind val kind: Int, eventBy: User, val status: Status) : Status {
    override val id: Long = timeAtMillis
    override val user: User = eventBy
    override val text: String = ""
    override val recipientScreenName: String = ""
    override val createdAt: Date = Date(timeAtMillis)
    override val source: String = ""
    override var favoritesCount: Int = 0
    override var repostsCount: Int = 0
    override val metadata: StatusPreforms = StatusPreforms()
    override val providerApiType: Int = Provider.API_SYSTEM
    override var representUser: AuthUserRecord
        get() = status.representUser
        set(value) {
            status.representUser = value
        }
    override var receivedUsers: MutableList<AuthUserRecord>
        get() = status.receivedUsers
        set(value) {
            status.receivedUsers = value
        }

    companion object {
        const val KIND_FAVED = 0
        const val KIND_RETWEETED = 1
    }
}