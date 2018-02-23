package shibafu.yukari.mastodon.entity

import android.text.Html
import android.text.format.Time
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*
import shibafu.yukari.entity.Status as IStatus

class DonStatus(val status: Status, override var representUser: AuthUserRecord) : IStatus {
    override val id: Long
        get() = status.id

    override val user: User = DonUser(status.account)

    @Suppress("DEPRECATION")
    override val text: String
        get() = Html.fromHtml(status.content).toString().trim(' ', '\n')

    override val recipientScreenName: String
        get() = representUser.ScreenName

    override val createdAt: Date = {
        val time = Time()
        time.parse3339(status.createdAt)
        Date(time.toMillis(false))
    }()

    override val source: String
        get() = status.application?.name ?: "Web"

    override val isRepost: Boolean
        get() = status.reblog != null

    override val originStatus: shibafu.yukari.entity.Status = if (isRepost) DonStatus(status.reblog!!, representUser) else this

    override val mentions: List<Mention> = status.mentions.map { DonMention(it) }

    override var favoritesCount: Int = status.favouritesCount

    override var repostsCount: Int = status.reblogsCount

    override val metadata: StatusPreforms = StatusPreforms()

    override val providerApiType: Int = Provider.API_MASTODON

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)
}
