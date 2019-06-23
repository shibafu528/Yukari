package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import android.util.Xml
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.api.entity.Status
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap
import org.threeten.bp.DateTimeUtils
import org.threeten.bp.ZonedDateTime
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.InReplyToId
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.StatusPreforms
import shibafu.yukari.entity.User
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.MediaFactory
import shibafu.yukari.media2.impl.DonPicture
import shibafu.yukari.media2.impl.DonVideo
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.MorseCodec
import java.io.StringReader
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

    override val createdAt: Date = DateTimeUtils.toDate(ZonedDateTime.parse(status.createdAt).toInstant())

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

    override val providerHost: String = representUser.Provider.host

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    val isLocal: Boolean = user.host == representUser.Provider.host

    var perProviderId = ObjectLongHashMap.newWithKeysValues(representUser.Provider.host, status.id)
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DonStatus) return false

        return this.status.uri == other.status.uri
    }

    override fun hashCode(): Int {
        return status.uri.hashCode()
    }

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
            if (userRecord.Provider.apiType != providerApiType) {
                return@forEach
            }

            status.mentions.forEach { entity ->
                val fullScreenName = MastodonUtil.expandFullScreenName(entity.acct, entity.url)
                if (userRecord.ScreenName == fullScreenName) {
                    return IStatus.RELATION_MENTIONED_TO_ME
                }
            }
            if (userRecord.Url == user.url) {
                return IStatus.RELATION_OWNED
            }
        }
        return IStatus.RELATION_NONE
    }

    override fun canRepost(userRecord: AuthUserRecord): Boolean {
        val originStatus = if (isRepost) status.reblog!! else status
        return userRecord.Provider.apiType == Provider.API_MASTODON &&
                (originStatus.visibility == Status.Visibility.Public.value || originStatus.visibility == Status.Visibility.Unlisted.value)
    }

    override fun canFavorite(userRecord: AuthUserRecord): Boolean {
        return userRecord.Provider.apiType == Provider.API_MASTODON
    }

    override fun merge(status: IStatus): IStatus {
        super.merge(status)

        // ローカルトゥートを優先
        if (status is DonStatus) {
            if (!this.isLocal && status.isLocal) {
                status.perProviderId.putAll(perProviderId)
                return status
            } else {
                perProviderId.putAll(status.perProviderId)
                return this
            }
        } else {
            return this
        }
    }

    override fun getInReplyTo(): InReplyToId {
        val inReplyTo = super.getInReplyTo()
        perProviderId.forEachKeyValue { key, value ->
            inReplyTo[Provider.API_MASTODON, key] = value.toString()
        }
        return inReplyTo
    }

    init {
        if (isRepost) {
            text = originStatus.text
            media = originStatus.media
            links = originStatus.links
        } else {
            val media = LinkedHashSet<Media>()
            val links = LinkedHashSet<String>()

            status.mediaAttachments.forEach { attachment ->
                when (attachment.type) {
                    "image" -> media += DonPicture(attachment)
                    "video", "gifv" -> media += DonVideo(attachment)
                    else -> links += attachment.remoteUrl ?: attachment.url
                }
            }

            val textContent = StringBuilder()

            if (status.spoilerText.isNotEmpty()) {
                textContent.append(status.spoilerText)
            }

            val xppFactory = XmlPullParserFactory.newInstance().apply {
                isValidating = false
                setFeature(Xml.FEATURE_RELAXED, true)
            }
            val xpp = xppFactory.newPullParser()
            xpp.setInput(StringReader("<div>" + status.content.replace('\u00A0', ' ') + "</div>"))

            var eventType = xpp.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (xpp.name) {
                            "br" -> textContent.append("\n")
                            "p" -> {
                                // spoilerの直後、または2つ目以降の段落開始前。空行を差し込む。
                                if (textContent.isNotEmpty()) {
                                    textContent.append("\n\n")
                                }
                            }
                            "a" -> {
                                val classes = xpp.getAttributeValue(null, "class") ?: ""
                                // not(.mention) -> 通常のリンクとして処理
                                if (!classes.contains("mention")) {
                                    val href = xpp.getAttributeValue(null, "href")
                                    if (!href.isNullOrEmpty()) {
                                        val m = MediaFactory.newInstance(href)
                                        if (m != null) {
                                            media += m
                                        } else {
                                            links += href
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> textContent.append(xpp.text)
                }

                eventType = xpp.next()
            }

            text = MorseCodec.decode(textContent.toString())

            this.media = media.toList()
            this.links = links.toList()
        }

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

            it.writeInt(perProviderId.size())
            perProviderId.forEachKeyValue { key, value ->
                it.writeString(key)
                it.writeLong(value)
            }
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

                val perProviderIdSize = source.readInt()
                val perProviderId = ObjectLongHashMap<String>(perProviderIdSize)
                for (i in 0 until perProviderIdSize) {
                    val key = source.readString()
                    val value = source.readLong()
                    perProviderId.put(key, value)
                }
                donStatus.perProviderId = perProviderId

                return donStatus
            }

            override fun newArray(size: Int): Array<DonStatus?> {
                return arrayOfNulls(size)
            }
        }
    }
    //</editor-fold>
}
