package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson
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
                      override val receiverUser: AuthUserRecord,
                      override val metadata: StatusPreforms = StatusPreforms()) : IStatus, Parcelable {
    override val id: Long
        get() = notification.id

    override val user: User = DonUser(notification.account)

    override val text: String
        get() = ""

    override val recipientScreenName: String
        get() = receiverUser.ScreenName

    override val createdAt: Date = Date.from(ZonedDateTime.parse(notification.createdAt).toInstant())

    override val source: String
        get() = ""

    override val favoritesCount: Int = 0

    override val repostsCount: Int = 0

    override val providerApiType: Int = Provider.API_MASTODON

    override val providerHost: String
        get() = representUser.Provider.host

    override var preferredOwnerUser: AuthUserRecord? = null

    override var prioritizedUser: AuthUserRecord? = null

    val status: DonStatus? = notification.status?.let { DonStatus(it, representUser) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DonNotification) return false

        return this.id == other.id && this.representUser.InternalId == other.representUser.InternalId
    }

    override fun hashCode(): Int {
        var result = representUser.InternalId.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(Gson().toJson(notification))
        dest.writeSerializable(receiverUser)
        dest.writeParcelable(metadata, 0)
        dest.writeSerializable(preferredOwnerUser)
        dest.writeSerializable(prioritizedUser)
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonNotification> {
            override fun createFromParcel(source: Parcel?): DonNotification {
                source!!
                val notification = Gson().fromJson(source.readString(), Notification::class.java)
                val receiverUser = source.readSerializable() as AuthUserRecord
                val metadata = source.readParcelable<StatusPreforms>(this.javaClass.classLoader)!!
                val preferredOwnerUser = source.readSerializable() as? AuthUserRecord
                val prioritizedUser = source.readSerializable() as? AuthUserRecord
                return DonNotification(notification, receiverUser, metadata).also {
                    it.preferredOwnerUser = preferredOwnerUser
                    it.prioritizedUser = prioritizedUser
                }
            }

            override fun newArray(size: Int): Array<DonNotification?> {
                return arrayOfNulls(size)
            }
        }
    }
}