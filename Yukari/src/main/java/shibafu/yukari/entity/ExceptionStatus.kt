package shibafu.yukari.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*

/**
 * 例外情報のラッパー
 */
class ExceptionStatus(override val id: Long,
                      override var representUser: AuthUserRecord,
                      val exception: Exception) : Status {
    override val user: User by lazy { object : User {
        override val id: Long = representUser.NumericId
        override val name: String = "Error"
        override val screenName: String = exception.javaClass.simpleName
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
    } }

    override val text: String = exception.message ?: "例外が発生しました"
    override val recipientScreenName: String = ""
    override val createdAt: Date = Date()
    override val source: String = "System"
    override val metadata: StatusPreforms = StatusPreforms()
    override val providerApiType: Int = Provider.API_SYSTEM
    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)
}