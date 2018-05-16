package shibafu.yukari.mastodon.entity

import com.sys1yagi.mastodon4j.api.entity.Account
import shibafu.yukari.entity.User

class DonUser(val account: Account?) : User {
    override val id: Long
        get() = account?.id ?: 0
    override val name: String
        get() = account?.displayName.takeIf { !it.isNullOrEmpty() } ?: account?.userName ?: ""
    override val screenName: String
        get() = account?.acct ?: ""
    override val isProtected: Boolean
        get() = account?.isLocked ?: false
    override val profileImageUrl: String
        get() = account?.avatar ?: ""
    override val biggerProfileImageUrl: String
        get() = account?.avatar ?: ""
}