package shibafu.yukari.entity

import shibafu.yukari.media2.Media
import shibafu.yukari.twitter.AuthUserRecord
import java.io.Serializable
import java.util.*

/**
 * タイムラインに表示できたりするやつ
 */
interface Status : Comparable<Status>, Serializable {
    /**
     * ID
     */
    val id: Long

    /**
     * URL
     */
    val url: String?
        get() = null

    /**
     * メッセージの所有者
     *
     * [isRepost] が真であるメッセージの場合、これは本来の発言者ではない場合がある。正確な情報は [originStatus] から取得する。
     */
    val user: User

    /**
     * メッセージ本文
     */
    val text: String

    /**
     * 代表受信アカウントのスクリーンネーム
     */
    val recipientScreenName: String

    /**
     * メッセージのタイムスタンプ
     */
    val createdAt: Date

    /**
     * メッセージの作成元アプリケーション名
     */
    val source: String

    /**
     * 引用して再投稿されたメッセージであるか？ (リツイートやブーストのこと)
     */
    val isRepost: Boolean
        get() = false

    /**
     * 再投稿メッセージの場合は引用元のメッセージ、そうでなければ自分自身
     */
    val originStatus: Status
        get() = this

    /**
     * 返信先の [Status] のID
     */
    val inReplyToId: Long
        get() = -1

    /**
     * 返信先の一覧
     */
    val mentions: List<Mention>
        get() = emptyList()

    /**
     * 添付画像(または動画)の一覧
     */
    val media: List<Media>
        get() = emptyList()

    /**
     * テキスト内のURLや、画像以外の追加情報など汎用な関連URLの一覧
     */
    val links: List<String>
        get() = emptyList()

    /**
     * ハッシュタグの一覧
     */
    val tags: List<String>
        get() = emptyList()

    /**
     * お気に入り登録数
     */
    var favoritesCount: Int

    /**
     * 引用数
     */
    var repostsCount: Int

    /**
     * メタデータ
     */
    val metadata: StatusPreforms

    /**
     * 対応する [shibafu.yukari.database.Provider.apiType] の値
     */
    val providerApiType: Int

    /**
     * 代表受信アカウント
     */
    var representUser: AuthUserRecord

    /**
     * 同一のステータスを受信した全てのアカウント
     */
    var receivedUsers: MutableList<AuthUserRecord>

    /**
     * 自分にとってどのような関係性のあるメッセージか判断
     */
    @StatusRelation
    fun getStatusRelation(userRecords: List<AuthUserRecord>): Int = RELATION_NONE

    /**
     * 自身の所有するステータスであるかどうか判定して、代表受信アカウントを書き換える
     */
    fun setRepresentIfOwned(userRecords: List<AuthUserRecord>) {
        userRecords.forEach { userRecord ->
            if (providerApiType == userRecord.Provider.apiType && user.url == userRecord.Url) {
                representUser = userRecord
                if (!receivedUsers.contains(userRecord)) {
                    receivedUsers.add(userRecord)
                }
                return
            }
        }
    }

    /**
     * このステータスがいずれかの受信アカウントにメンションを向けているか判断
     */
    fun isMentionedToMe(): Boolean {
        return getStatusRelation(receivedUsers) == RELATION_MENTIONED_TO_ME
    }

    /**
     * このステータスが代表受信アカウントのものであるか(削除等の強い権限を持っている)どうか判定
     */
    fun isOwnedStatus(): Boolean {
        return providerApiType == representUser.Provider.apiType && user.url == representUser.Url
    }

    /**
     * いずれかの受信アカウントによって、このステータスがお気に入り登録されているか？
     */
    fun isFavoritedSomeone(): Boolean {
        return receivedUsers.map { it.NumericId }.any { metadata.favoritedUsers.get(it) }
    }

    /**
     * 指定したアカウントで、このステータスを再投稿できるか？
     */
    fun canRepost(userRecord: AuthUserRecord): Boolean = false

    /**
     * 指定したアカウントで、このステータスをお気に入りできるか？
     */
    fun canFavorite(userRecord: AuthUserRecord): Boolean = false

    /**
     * 同じ内容を指す、より新しい別インスタンスの情報と比較してなるべく最新かつ情報の完全性が高いインスタンスを返す
     */
    fun merge(status: Status): Status {
        if (this != status || this.providerApiType != status.providerApiType) {
            throw IllegalArgumentException("マージは両インスタンスがEqualsかつAPI Typeが揃っていないと実行できません。this[URL=$url, API=$providerApiType] : args[URL=${status.url}, API=${status.providerApiType}]")
        }

        favoritesCount = status.favoritesCount
        repostsCount = status.repostsCount

        status.receivedUsers.forEach { userRecord ->
            if (!receivedUsers.contains(userRecord)) {
                receivedUsers.add(userRecord)
            }
            if (status.metadata.favoritedUsers.get(userRecord.NumericId)) {
                metadata.favoritedUsers.put(userRecord.NumericId, true)
            }
        }
        status.receivedUsers = receivedUsers
        status.metadata.favoritedUsers = metadata.favoritedUsers

        return this
    }

    /**
     * ShareTwitterOnTumblr形式に変換する
     */
    fun toSTOTFormat(): String {
        val origin = originStatus
        return buildString {
            append(origin.user.screenName)
            append(":")
            append(origin.text)
            append(" [")
            append(origin.url)
            append("]")
        }
    }

    fun getInReplyToId(): InReplyToId = InReplyToId(url.orEmpty())

    override fun compareTo(other: Status): Int {
        if (this === other) return 0

        // タイムスタンプで比較
        if (this.createdAt == other.createdAt) {
            // 同一時刻の場合はAPI Type順に並べる
            if (this.providerApiType == other.providerApiType) {
                val thisHash = this.hashCode()
                val otherHash = other.hashCode()

                if (thisHash == otherHash) throw RuntimeException()
                if (thisHash < otherHash) return -1
                return 1
            }
            if (this.providerApiType < other.providerApiType) return -1
            return 1
        }
        if (this.createdAt < other.createdAt) return -1
        return 1
    }

    companion object {
        /** Relation: 無関係 */
        const val RELATION_NONE = 0
        /** Relation: 自身の発言 */
        const val RELATION_OWNED = 1
        /** Relation: 自分に向けられたメッセージ */
        const val RELATION_MENTIONED_TO_ME = 2
    }
}
