package shibafu.yukari.mastodon.entity

import android.text.Html
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import java.text.SimpleDateFormat
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
        // TODO: これは古いAndroidでも動作するのか？
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        sdf.parse(status.createdAt)
    }()

    override val source: String
        get() = status.application?.name ?: "Web"

    override val mentions: List<Mention> = status.mentions.map { DonMention(it) }

    override var favoritesCount: Int = status.favouritesCount

    override var repostsCount: Int = status.reblogsCount

    override val metadata: StatusPreforms = StatusPreforms()

    override val providerApiType: Int = Provider.API_MASTODON

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)
}
