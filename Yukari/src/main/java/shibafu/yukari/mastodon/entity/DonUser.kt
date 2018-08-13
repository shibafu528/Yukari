package shibafu.yukari.mastodon.entity

import com.sys1yagi.mastodon4j.api.entity.Account
import shibafu.yukari.entity.User
import shibafu.yukari.mastodon.MastodonUtil

class DonUser(val account: Account?) : User {
    override val id: Long = account?.id ?: 0
    override val url: String? = account?.url
    override val name: String = account?.displayName.takeIf { !it.isNullOrEmpty() } ?: account?.userName ?: ""
    override val screenName: String = account?.let { MastodonUtil.expandFullScreenName(it.acct, it.url) } ?: ""
    override val isProtected: Boolean = account?.isLocked ?: false
    override val profileImageUrl: String = account?.avatar ?: ""
    override val biggerProfileImageUrl: String = account?.avatar ?: ""
}