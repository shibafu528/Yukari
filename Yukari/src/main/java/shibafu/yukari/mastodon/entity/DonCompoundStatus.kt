package shibafu.yukari.mastodon.entity

import android.os.Parcel
import android.os.Parcelable
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.InReplyToId
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.media2.Media
import java.util.Date
import com.sys1yagi.mastodon4j.api.entity.Status.Visibility as StatusVisibility

/**
 * 同一のURIを持つ複数の[DonStatus]を1つにまとめたもの。
 * 受信サーバーによって異なる属性を持つ場合があり、受信者コンテキストのみを書き換えてマージするのが不可能なので、マージした[DonStatus]を全て保持することで対応している。
 */
class DonCompoundStatus(
    statuses: List<DonStatus>,
    private var _representUser: AuthUserRecord? = null,
    private var _representOverrode: Boolean? = null
) : Status, Parcelable {
    constructor(first: DonStatus, second: DonStatus) : this(upsertStatusBy(listOf(first), second))

    private val statuses = statuses.sortedWith(COMPARATOR)
    val representStatus = this.statuses.first()

    override val id: Long
        get() = representStatus.id
    override val url: String
        get() = representStatus.url
    override val user: User
        get() = representStatus.user
    override val text: String
        get() = representStatus.text
    override val recipientScreenName: String
        get() = representStatus.recipientScreenName
    override val createdAt: Date
        get() = representStatus.createdAt
    override val source: String
        get() = representStatus.source
    override val isRepost: Boolean
        get() = representStatus.isRepost
    override val originStatus: Status
        get() = representStatus.originStatus
    override val inReplyToId: Long
        get() = representStatus.inReplyToId
    override val mentions: List<Mention>
        get() = representStatus.mentions
    override val media: List<Media>
        get() = representStatus.media
    override val links: List<String>
        get() = representStatus.links
    override val tags: List<String>
        get() = representStatus.tags

    private var _favoritesCount: Int? = null
    override var favoritesCount: Int
        get() = _favoritesCount ?: representStatus.favoritesCount
        set(value) {
            _favoritesCount = value
        }

    private var _repostsCount: Int? = null
    override var repostsCount: Int
        get() = _repostsCount ?: representStatus.repostsCount
        set(value) {
            _repostsCount = value
        }

    override val metadata = representStatus.metadata.clone().also { sp ->
        statuses.drop(1).forEach { status ->
            sp.favoritedUsers.putAll(status.metadata.favoritedUsers)
        }
    }

    override val providerApiType: Int
        get() = representStatus.providerApiType
    override val providerHost: String
        get() = representStatus.providerHost

    override var representUser: AuthUserRecord
        get() = _representUser ?: representStatus.representUser
        set(value) {
            _representUser = value
        }
    override var representOverrode: Boolean
        get() = _representOverrode ?: representStatus.representOverrode
        set(value) {
            _representOverrode = value
        }

    override var receivedUsers: MutableList<AuthUserRecord>
        get() = statuses.flatMap { it.receivedUsers }.let { list -> _representUser?.let { user -> list + user } ?: list }.distinct().toMutableList()
        set(_) {} // Status#clone, Status#merge でしか使われていない。外部からの上書きを許す理由がないので無視。

    private val _inReplyTo: InReplyToId by lazy {
        val inReplyTo = representStatus.getInReplyTo()
        statuses.drop(1).forEach { status ->
            status.perProviderId.forEachKeyValue { key, value ->
                if (inReplyTo[Provider.API_MASTODON, key] == null) {
                    inReplyTo[Provider.API_MASTODON, key] = value.toString()
                }
            }
        }
        inReplyTo
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when (other) {
            is DonStatus -> this.representStatus.status.uri == other.status.uri
            is DonCompoundStatus -> this.representStatus.status.uri == other.representStatus.status.uri
            else -> false
        }
    }

    override fun hashCode(): Int {
        return representStatus.status.uri.hashCode()
    }

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        userRecords.forEach { userRecord ->
            if (userRecord.Provider.apiType != providerApiType) {
                return@forEach
            }

            representStatus.mentions.forEach { entity ->
                if (userRecord.ScreenName == entity.screenName) {
                    return Status.RELATION_MENTIONED_TO_ME
                }
            }
            if (userRecord.Url == user.url) {
                return Status.RELATION_OWNED
            }
        }
        return Status.RELATION_NONE
    }

    override fun canRepost(userRecord: AuthUserRecord): Boolean {
        val originStatus = (originStatus as DonStatus).status
        return userRecord.Provider.apiType == Provider.API_MASTODON &&
                (originStatus.visibility == StatusVisibility.Public.value || originStatus.visibility == StatusVisibility.Unlisted.value)
    }

    override fun canFavorite(userRecord: AuthUserRecord): Boolean {
        return userRecord.Provider.apiType == Provider.API_MASTODON
    }

    override fun merge(status: Status): Status {
        // immutableなマージ処理を行いたいので super.merge() は呼び出さない。

        if (this === status) {
            return this
        }

        if (status !is DonStatus) {
            throw IllegalArgumentException("DonStatusのみマージすることができます")
        }

        return DonCompoundStatus(upsertStatusBy(statuses, status), _representUser, _representOverrode)
    }

    override fun getInReplyTo(): InReplyToId = _inReplyTo

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(statuses.size)
        statuses.forEach { dest.writeParcelable(it, 0) }
        dest.writeSerializable(_representUser)
        dest.writeInt(
            when (_representOverrode) {
                null -> -1
                false -> 0
                true -> 1
            }
        )
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonCompoundStatus> {
            override fun createFromParcel(source: Parcel): DonCompoundStatus {
                val statusesSize = source.readInt()
                val statuses = (0..<statusesSize).map {
                    source.readParcelable<DonStatus>(javaClass.classLoader)!!
                }
                val representUser = source.readSerializable() as? AuthUserRecord
                val representOverrode = when (source.readInt()) {
                    -1 -> null
                    0 -> false
                    else -> true
                }

                return DonCompoundStatus(statuses, representUser, representOverrode)
            }

            override fun newArray(size: Int): Array<DonCompoundStatus?> {
                return arrayOfNulls(size)
            }
        }

        private val BY_OWNED_STATUS = Comparator.comparing<DonStatus, _> { !it.isOwnedStatus() }
        private val BY_REPRESENT_OVERRODE = Comparator.comparing<DonStatus, _> { !it.representOverrode }
        private val BY_MENTIONED = Comparator.comparing<DonStatus, _> { status ->
            status.mentions.find { it.isMentionedTo(status.firstReceiverUser) } == null
        }
        private val BY_PRIMARY_ACCOUNT_RECEIVED = Comparator.comparing<DonStatus, _> { !it.firstReceiverUser.isPrimary }
        private val BY_LOCAL_STATUS = Comparator.comparing<DonStatus, _> { !it.isLocal }
        private val BY_RECEIVER_ID = Comparator.comparingLong<DonStatus> { status ->
            status.firstReceiverUser.InternalId
        }

        private val COMPARATOR = BY_OWNED_STATUS
            .then(BY_REPRESENT_OVERRODE)
            .then(BY_MENTIONED)
            .then(BY_PRIMARY_ACCOUNT_RECEIVED)
            .then(BY_LOCAL_STATUS)
            .then(BY_RECEIVER_ID)

        private fun upsertStatusBy(statuses: List<DonStatus>, newStatus: DonStatus): List<DonStatus> {
            var replaced = false
            val newStatuses = mutableListOf<DonStatus>()
            statuses.forEach { s ->
                if (s.url == newStatus.url && s.firstReceiverUser == newStatus.firstReceiverUser) {
                    replaced = true
                    newStatuses += newStatus
                } else {
                    newStatuses += s
                }
            }
            if (!replaced) {
                newStatuses += newStatus
            }
            return newStatuses
        }
    }
}