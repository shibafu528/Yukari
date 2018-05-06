package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import android.text.Html
import android.text.format.Time
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*
import shibafu.yukari.entity.Status as IStatus

class DonStatus(val status: Status,
                override var representUser: AuthUserRecord,
                override val metadata: StatusPreforms = StatusPreforms()) : IStatus, Parcelable {
    override val id: Long
        get() = status.id

    override val url: String = status.url

    override val user: User = DonUser(status.account)

    @Suppress("DEPRECATION")
    override val text: String
        get() {
            val html = if (status.spoilerText.isEmpty()) {
                status.content
            } else {
                status.spoilerText + "<p></p>"  + status.content
            }
            return Html.fromHtml(html).toString().trim(' ', '\n')
        }

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

    override val inReplyToId: Long = status.inReplyToId ?: -1

    override val mentions: List<Mention> = status.mentions.map { DonMention(it) }

    override val tags: List<String> = status.tags.map { "#" + it.name }

    override var favoritesCount: Int = status.favouritesCount

    override var repostsCount: Int = status.reblogsCount

    override val providerApiType: Int = Provider.API_MASTODON

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
            status.mentions.forEach { entity ->
                if (userRecord.NumericId == entity.id) {
                    return IStatus.RELATION_MENTIONED_TO_ME
                }
            }
            if (userRecord.NumericId == status.inReplyToAccountId) {
                return IStatus.RELATION_MENTIONED_TO_ME
            }
            if (userRecord.NumericId == user.id) {
                return IStatus.RELATION_OWNED
            }
        }
        return IStatus.RELATION_NONE
    }

    //<editor-fold desc="Parcelable">
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.let {
            it.writeString(Gson().toJson(status))
            it.writeSerializable(representUser)
            it.writeParcelable(metadata, 0)
            it.writeInt(favoritesCount)
            it.writeInt(repostsCount)
            it.writeList(receivedUsers)
        }
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonStatus> {
            override fun createFromParcel(source: Parcel?): DonStatus {
                source!!
                val status = Gson().fromJson(source.readString(), Status::class.java)
                val representUser = source.readSerializable() as AuthUserRecord
                val metadata = source.readParcelable<StatusPreforms>(this.javaClass.classLoader)
                val donStatus = DonStatus(status, representUser, metadata)
                donStatus.favoritesCount = source.readInt()
                donStatus.repostsCount = source.readInt()
                donStatus.receivedUsers = source.readArrayList(this.javaClass.classLoader) as MutableList<AuthUserRecord>
                return donStatus
            }

            override fun newArray(size: Int): Array<DonStatus?> {
                return arrayOfNulls(size)
            }
        }
    }
    //</editor-fold>
}
