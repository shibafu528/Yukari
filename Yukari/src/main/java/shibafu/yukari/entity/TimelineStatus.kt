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
    prioritizedUser: AuthUserRecord? = null
) : Status, Parcelable where T : Status, T : MergeableStatus {
    constructor(status: T) : this(listOf(status))

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
    override val favoritesCount: Int
        get() = representStatus.favoritesCount
    override val repostsCount: Int
        get() = representStatus.repostsCount

    override val metadata = representStatus.metadata.clone().also { sp ->
        statuses.drop(1).forEach { status ->
            sp.favoritedUsers.putAll(status.metadata.favoritedUsers)
        }
    }

    override val providerApiType: Int
        get() = representStatus.providerApiType
    override val providerHost: String
        get() = representStatus.providerHost
    override val receiverUser: AuthUserRecord
        get() = representStatus.receiverUser

    override var preferredOwnerUser: AuthUserRecord?
        get() = representStatus.preferredOwnerUser
        set(_) = throw UnsupportedOperationException()

    override var prioritizedUser: AuthUserRecord? = prioritizedUser ?: representStatus.prioritizedUser

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
        return when (other) {
            is TimelineStatus<*> -> representStatus.javaClass == other.representStatus.javaClass && representStatus == other.representStatus
            else -> representStatus.javaClass == other?.javaClass && representStatus == other
        }
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

    fun merge(status: Status): Status {
        if (this === status) {
            return this
        }

        val givenStatuses = when (status) {
            is TimelineStatus<*> -> status.statuses
            is MergeableStatus -> listOf(status)
            else -> throw IllegalArgumentException("マージするにはTimelineStatusであるか、MergeableStatusを実装している必要があります")
        }

        givenStatuses.forEach { s ->
            if (representStatus.javaClass != s.javaClass) {
                throw IllegalArgumentException("同じ型同士のStatusでないとマージできません (receiver = ${representStatus.javaClass}, given = ${status.javaClass})")
            }
        }

        @Suppress("UNCHECKED_CAST")
        return TimelineStatus(upsertStatusBy(statuses, givenStatuses) as List<T>, prioritizedUser)
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
        dest.writeSerializable(prioritizedUser)
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
                val prioritizedUser = source.readSerializable() as? AuthUserRecord

                return TimelineStatus(statuses, prioritizedUser)
            }

            override fun newArray(size: Int): Array<TimelineStatus<*>?> {
                return arrayOfNulls(size)
            }
        }

        private fun <T : Status> upsertStatusBy(statuses: List<T>, newStatuses: List<T>): List<T> {
            val lefts = newStatuses.toMutableList()
            val result = mutableListOf<T>()

            statuses.forEach { exist ->
                val iterator = lefts.listIterator()
                while (iterator.hasNext()) {
                    val given = iterator.next()
                    if (exist.url == given.url && exist.representUser == given.representUser) {
                        result += given
                        iterator.remove()
                        return@forEach
                    }
                }

                result += exist
            }
            result.addAll(lefts)

            return result
        }
    }
}