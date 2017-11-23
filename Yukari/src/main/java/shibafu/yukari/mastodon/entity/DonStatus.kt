package shibafu.yukari.mastodon.entity

import android.text.Html
import android.text.format.Time
import com.sys1yagi.mastodon4j.api.entity.Account
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*
import shibafu.yukari.entity.Status as IStatus

class DonStatus(val status: Status, val receivedUser: AuthUserRecord) : IStatus {
    override val id: Long
        get() = status.id
    override val user: User by lazy { DonUser(status.account) }
    override val text: String
        get() = Html.fromHtml(status.content).toString().trim(' ', '\n')
    override val recipientScreenName: String
        get() = receivedUser.ScreenName
    override val createdAt: Date by lazy {
        val time = Time()
        time.parse3339(status.createdAt)
        Date(time.toMillis(false))
    }
    override val source: String
        get() = status.application?.name ?: "Web"
}

class DonUser(val account: Account?) : User {
    override val id: Long
        get() = account?.id ?: 0
    override val name: String
        get() = account?.displayName ?: ""
    override val screenName: String
        get() = account?.userName ?: ""
    override val isProtected: Boolean
        get() = account?.isLocked ?: false
    override val profileImageUrl: String
        get() = account?.avatar ?: ""
    override val biggerProfileImageUrl: String
        get() = account?.avatar ?: ""
}