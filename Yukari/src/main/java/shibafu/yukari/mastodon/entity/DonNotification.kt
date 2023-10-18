package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import com.sys1yagi.mastodon4j.api.entity.Notification
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status as IStatus
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import java.time.ZonedDateTime
import java.util.Date

/**
 * Mastodonの通知。
 *
 * [API Document](https://docs.joinmastodon.org/entities/Notification/)
 */
class DonNotification(val notification: Notification,
                      override var representUser: AuthUserRecord,
                      override val metadata: StatusPreforms = StatusPreforms()) : IStatus, Parcelable {
    override val id: Long
        get() = notification.id

    override val user: User = DonUser(notification.account)

    override val text: String
        get() = ""

    override val recipientScreenName: String
        get() = representUser.ScreenName

    override val createdAt: Date = Date.from(ZonedDateTime.parse(notification.createdAt).toInstant())

    override val source: String
        get() = ""

    override var favoritesCount: Int = 0

    override var repostsCount: Int = 0

    override val providerApiType: Int = Provider.API_MASTODON

    override val providerHost: String
        get() = representUser.Provider.host

    override var representOverrode: Boolean = false

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    val status: DonStatus? = notification.status?.let { DonStatus(it, representUser) }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonNotification> {
            override fun createFromParcel(source: Parcel?): DonNotification {
                TODO("Not yet implemented")
            }

            override fun newArray(size: Int): Array<DonNotification?> {
                return arrayOfNulls(size)
            }
        }
    }
}