package shibafu.yukari.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*

/**
 * ステータスのモック
 */
open class MockStatus(override val id: Long, override var representUser: AuthUserRecord) : Status {
    override val user: User = object : User {
        override val id: Long = this@MockStatus.id
        override val name: String = "Error"
        override val screenName: String = MockStatus::class.java.simpleName
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
    }

    override val text: String = ""
    override val recipientScreenName: String = ""
    override val createdAt: Date = Date()
    override val source: String = "System"
    override var favoritesCount: Int = 0
    override var repostsCount: Int = 0
    override val metadata: StatusPreforms = StatusPreforms()
    override val providerApiType: Int = Provider.API_SYSTEM
    override val providerHost: String = HOST
    override var representOverrode: Boolean = false
    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    companion object {
        const val HOST = "mock.yukari.internal"
    }
}