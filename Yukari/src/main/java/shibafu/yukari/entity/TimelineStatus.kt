package shibafu.yukari.entity

import android.os.Parcel
import android.os.Parcelable
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.media2.Media
import java.util.Date

/**
 * タイムライン上で同一の [Status] を1つにまとめるためのもの。
 */
class TimelineStatus<T>(
    statuses: List<T>,
    private var _representUser: AuthUserRecord? = null,
    private var _representOverrode: Boolean? = null
) : Status, Parcelable where T : Status, T : MergeableStatus {
    constructor(status: T) : this(listOf(status))
    constructor(first: T, second: T) : this(upsertStatusBy(listOf(first), second))

    private val statuses = statuses.sortedWith { lhs, rhs ->
        StatusComparator.BY_OWNED_STATUS.compare(lhs, rhs).let { if (it != 0) return@sortedWith it }
        StatusComparator.BY_MENTIONED.compare(lhs, rhs).let { if (it != 0) return@sortedWith it }
        StatusComparator.BY_PRIMARY_ACCOUNT_RECEIVED.compare(lhs, rhs).let { if (it != 0) return@sortedWith it }
        lhs.compareMergePriorityTo(rhs).let { if (it != 0) return@sortedWith it }
        StatusComparator.BY_RECEIVER_ID.compare(lhs, rhs)
    }
    val representStatus = this.statuses.first()

    override val id: Long
        get() = representStatus.id
    override val url: String?
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
            status.getInReplyTo().entries.forEach { (key, value) ->
                if (inReplyTo[Provider.API_MASTODON, key] == null) {
                    inReplyTo[Provider.API_MASTODON, key] = value
                }
            }
        }
        inReplyTo
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return representStatus.javaClass == other?.javaClass && representStatus == other
    }

    override fun hashCode(): Int {
        return representStatus.hashCode()
    }

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Int {
        statuses.forEach { status ->
            val relation = status.getStatusRelation(userRecords)
            if (relation != Status.RELATION_NONE) {
                return relation
            }
        }
        return Status.RELATION_NONE
    }

    override fun canRepost(userRecord: AuthUserRecord): Boolean {
        return representStatus.canRepost(userRecord)
    }

    override fun canFavorite(userRecord: AuthUserRecord): Boolean {
        return representStatus.canFavorite(userRecord)
    }

    override fun merge(status: Status): Status {
        // immutableなマージ処理を行いたいので super.merge() は呼び出さない。

        if (this === status) {
            return this
        }

        if (status !is MergeableStatus) {
            throw IllegalArgumentException("マージするにはMergeableStatusを実装している必要があります")
        }
        if (representStatus.javaClass != status.javaClass) {
            throw IllegalArgumentException("同じ型同士のStatusでないとマージできません (receiver = ${representStatus.javaClass}, given = ${status.javaClass})")
        }

        return TimelineStatus(upsertStatusBy(statuses, status), _representUser, _representOverrode)
    }

    override fun getInReplyTo(): InReplyToId = _inReplyTo

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(statuses.size)
        statuses.forEach { status ->
            if (status is Parcelable) {
                dest.writeInt(1)
                dest.writeParcelable(status, 0)
            } else {
                dest.writeInt(0)
                dest.writeSerializable(status)
            }
        }
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
        @JvmField val CREATOR = object : Parcelable.Creator<TimelineStatus<*>> {
            override fun createFromParcel(source: Parcel): TimelineStatus<*> {
                val statusesSize = source.readInt()
                val statuses = (0..<statusesSize).map {
                    val status = when (source.readInt()) {
                        0 -> source.readSerializable()
                        1 -> source.readParcelable(javaClass.classLoader)!!
                        else -> throw RuntimeException("invalid type code")
                    }
                    if (status is Status && status is MergeableStatus) {
                        status
                    } else {
                        throw RuntimeException("Status, MergeableStatusを実装していないオブジェクト")
                    }
                }
                val representUser = source.readSerializable() as? AuthUserRecord
                val representOverrode = when (source.readInt()) {
                    -1 -> null
                    0 -> false
                    else -> true
                }

                return TimelineStatus(statuses, representUser, representOverrode)
            }

            override fun newArray(size: Int): Array<TimelineStatus<*>?> {
                return arrayOfNulls(size)
            }
        }

        private fun <T : Status> upsertStatusBy(statuses: List<T>, newStatus: T): List<T> {
            var replaced = false
            val newStatuses = mutableListOf<T>()
            statuses.forEach { s ->
                // TODO: 確実に最初の受信者だと分かるもののほうがよいかも DonStatusのfirstReceiverみたいな
                if (s.url == newStatus.url && s.representUser == newStatus.representUser) {
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