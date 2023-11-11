package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import android.util.Xml
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.api.entity.Status
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import shibafu.yukari.database.Provider
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.MediaFactory
import shibafu.yukari.media2.impl.DonPicture
import shibafu.yukari.media2.impl.DonVideo
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.*
import shibafu.yukari.util.MorseCodec
import shibafu.yukari.util.readBooleanCompat
import shibafu.yukari.util.writeBooleanCompat
import java.io.StringReader
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.LinkedHashSet
import shibafu.yukari.entity.Status as IStatus

class DonStatus(val status: Status,
                override var representUser: AuthUserRecord,
                override val metadata: StatusPreforms = StatusPreforms()) : IStatus, MergeableStatus, Parcelable, PluginApplicable {
    override val id: Long
        get() = status.id

    override val url: String = status.url

    override val user: User = DonUser(status.account)

    override val text: String

    override val recipientScreenName: String
        get() = representUser.ScreenName

    override val createdAt: Date = Date.from(ZonedDateTime.parse(status.createdAt).toInstant())

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

    override var representOverrode: Boolean = false

    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)

    override val isApplicablePlugin: Boolean
        get() = when (status.visibility) {
            Status.Visibility.Public.value, Status.Visibility.Unlisted.value -> true
            else -> false
        }

    val isLocal: Boolean = user.host == representUser.Provider.host

    var perProviderId: ObjectLongHashMap<String> = ObjectLongHashMap.newWithKeysValues(representUser.Provider.host, status.id)
        private set

    /**
     * この [DonStatus] オブジェクトを作成した時点の受信アカウント。
     */
    private val firstReceiverUser = representUser

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DonStatus) return false

        return this.status.uri == other.status.uri
    }

    override fun hashCode(): Int {
        return status.uri.hashCode()
    }

    override fun clone(): IStatus {
        val s = super.clone() as DonStatus
        s.perProviderId = ObjectLongHashMap(perProviderId)
        return s
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
        if (this === status) {
            return this
        }

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

    override fun compareMergePriorityTo(other: IStatus): Int {
        if (other !is DonStatus) throw IllegalArgumentException()
        return BY_LOCAL_STATUS.compare(this, other)
    }

    fun checkProviderHostMismatching() {
        val localId = perProviderId[providerHost]
        if (id != localId) {
            val expected = perProviderId.keyValuesView().first { pair -> pair.two == status.id }
            throw ProviderHostMismatchedException(expected.one, providerHost)
        }
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

            var insertLineBreakBeforeNewParagraph = false
            if (status.spoilerText.isNotEmpty()) {
                textContent.append(status.spoilerText)
                insertLineBreakBeforeNewParagraph = true
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
                                    insertLineBreakBeforeNewParagraph = true
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
                    XmlPullParser.TEXT -> {
                        if (insertLineBreakBeforeNewParagraph) {
                            textContent.append("\n\n")
                            insertLineBreakBeforeNewParagraph = false
                        }
                        textContent.append(xpp.text)
                    }
                }

                eventType = xpp.next()
            }

            text = MorseCodec.decode(textContent.toString())

            this.media = media.toList()
            this.links = links.toList()
        }

        metadata.isCensoredThumbs = status.isSensitive
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(Gson().toJson(status))
        dest.writeSerializable(firstReceiverUser)
        dest.writeSerializable(representUser)
        dest.writeParcelable(metadata, 0)
        dest.writeInt(favoritesCount)
        dest.writeInt(repostsCount)
        dest.writeBooleanCompat(representOverrode)
        dest.writeList(receivedUsers.toList())

        dest.writeInt(perProviderId.size())
        perProviderId.forEachKeyValue { key, value ->
            dest.writeString(key)
            dest.writeLong(value)
        }
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonStatus> {
            override fun createFromParcel(source: Parcel?): DonStatus {
                source!!
                val status = Gson().fromJson(source.readString(), Status::class.java)
                val firstReceiverUser = source.readSerializable() as AuthUserRecord
                val representUser = source.readSerializable() as AuthUserRecord
                val metadata = source.readParcelable<StatusPreforms>(this.javaClass.classLoader)!!
                val donStatus = DonStatus(status, firstReceiverUser, metadata)
                donStatus.representUser = representUser
                donStatus.favoritesCount = source.readInt()
                donStatus.repostsCount = source.readInt()
                donStatus.representOverrode = source.readBooleanCompat()
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

        private val BY_LOCAL_STATUS = Comparator.comparing<DonStatus, _> { !it.isLocal }
    }

    class ProviderHostMismatchedException(expected: String, actual: String) : RuntimeException("[BUG] provider host mismatched!! expected = $expected, actual = $actual")
}
