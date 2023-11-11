package shibafu.yukari.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.database.AuthUserRecord
import java.util.*

/**
 * ステータスのモック
 */
open class MockStatus(override val id: Long, override val receiverUser: AuthUserRecord) : Status {
    override val user: User = object : User {
        override val id: Long = this@MockStatus.id
        override val name: String = "Error"
        override val screenName: String = MockStatus::class.java.simpleName
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
        override fun isMentionedTo(userRecord: AuthUserRecord): Boolean = false
    }

    override val text: String = ""
    override val recipientScreenName: String = ""
    override val createdAt: Date = Date()
    override val source: String = "System"
    override val favoritesCount: Int = 0
    override val repostsCount: Int = 0
    override val metadata: StatusPreforms = StatusPreforms()
    override val providerApiType: Int = Provider.API_SYSTEM
    override val providerHost: String = HOST
    override var preferredOwnerUser: AuthUserRecord? = null
    override var prioritizedUser: AuthUserRecord? = null

    companion object {
        const val HOST = "mock.yukari.internal"
    }
}