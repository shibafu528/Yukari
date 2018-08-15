package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import android.text.format.Time
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.api.entity.Status
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Whitelist
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.impl.DonPicture
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*
import kotlin.collections.LinkedHashSet
import shibafu.yukari.entity.Status as IStatus

class DonStatus(val status: Status,
                override var representUser: AuthUserRecord,
                override val metadata: StatusPreforms = StatusPreforms()) : IStatus, Parcelable {
    override val id: Long
        get() = status.id

    override val url: String = status.url

    override val user: User = DonUser(status.account)

    override val text: String

    override val recipientScreenName: String
        get() = representUser.ScreenName

    override val createdAt: Date = {
        val time = Time()
        time.parse3339(status.createdAt)
        Date(time.toMillis(false))
    }()

    override val source: String
        get() = status.application?.name ?: ""

    override val isRepost: Boolean
        get() = status.reblog != null

    override val originStatus: shibafu.yukari.entity.Status = if (isRepost) DonStatus(status.reblog!!, representUser) else this

    override val inReplyToId: Long = status.inReplyToId ?: -1

    override val mentions: List<Mention> = status.mentions.map { DonMention(it) }

    override val media: List<Media>

    override val links: List<String>

    override val tags: List<String> = status.tags.map { it.name }

    override var favoritesCount: Int = status.favouritesCount

    override var repostsCount: Int = status.reblogsCount

    override val providerApiType: Int = Provider.API_MASTODON

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    val isLocal: Boolean = status.application != null

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
            if (userRecord.Provider.apiType != providerApiType) {
                return@forEach
            }

            status.mentions.forEach { entity ->
                if (userRecord.NumericId == entity.id) {
                    return IStatus.RELATION_MENTIONED_TO_ME
                }
            }
            if (userRecord.NumericId == status.inReplyToAccountId) {
                return IStatus.RELATION_MENTIONED_TO_ME
            }
            if (userRecord.NumericId == user.id && userRecord.Provider.host == user.host) {
                return IStatus.RELATION_OWNED
            }
        }
        return IStatus.RELATION_NONE
    }

    override fun merge(status: IStatus): IStatus {
        super.merge(status)

        // ローカルトゥートを優先
        if (status is DonStatus && !this.isLocal && status.isLocal) {
            return status
        } else {
            return this
        }
    }

    private fun Document.plainText(): String {
        // https://stackoverflow.com/a/19602313
        val doc = this.clone()
        val outputSettings = Document.OutputSettings().prettyPrint(false)
        doc.outputSettings(outputSettings)
        doc.select("br").append("\\n")
        doc.select("p").append("\\n\\n")
        val s = doc.html().replace("\\n", "\n")
        return Jsoup.clean(s, "", Whitelist.none(), outputSettings).trim('\n')
    }

    init {
        val content = Jsoup.parse(status.content)

        if (status.spoilerText.isNotEmpty()) {
            text = status.spoilerText + "\n\n" + content.plainText()
        } else {
            text = content.plainText()
        }

        val media = LinkedHashSet<Media>()
        val links = LinkedHashSet<String>()

        status.mediaAttachments.forEach { attachment ->
            // TODO: videoとかgifvとかは...?
            when (attachment.type) {
                "image" -> media += DonPicture(attachment)
                else -> links += attachment.remoteUrl ?: attachment.url
            }
        }

        content.select("a:not(.mention)").forEach { element ->
            val href = element.attr("href")
            if (!href.isNullOrEmpty()) {
                links += href
            }
        }

        this.media = media.toList()
        this.links = links.toList()

        metadata.isCensoredThumbs = status.isSensitive
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
