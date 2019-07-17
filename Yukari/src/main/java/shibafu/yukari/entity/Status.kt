package shibafu.yukari.entity

import shibafu.yukari.media2.Media
import shibafu.yukari.twitter.AuthUserRecord
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList

/**
 * タイムラインに表示できたりするやつ
 */
interface Status : Comparable<Status>, Serializable, Cloneable {
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
     * このステータスを受信した [shibafu.yukari.database.Provider] のホスト名
     */
    val providerHost: String

    /**
     * 代表受信アカウント
     */
    var representUser: AuthUserRecord

    /**
     * 代表受信アカウントが高優先度なアカウントでロックされているかどうか
     */
    var representOverrode: Boolean

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
            if (providerApiType == userRecord.Provider.apiType && originStatus.user.url == userRecord.Url) {
                representUser = userRecord
                representOverrode = true
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
        return receivedUsers.map { it.InternalId }.any { metadata.favoritedUsers.get(it) }
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
        if (this === status) {
            return this
        }

        if (this != status || this.providerApiType != status.providerApiType) {
            throw IllegalArgumentException("マージは両インスタンスがEqualsかつAPI Typeが揃っていないと実行できません。this[URL=$url, API=$providerApiType] : args[URL=${status.url}, API=${status.providerApiType}]")
        }

        favoritesCount = status.favoritesCount
        repostsCount = status.repostsCount

        status.receivedUsers.forEach { userRecord ->
            if (!receivedUsers.contains(userRecord)) {
                receivedUsers.add(userRecord)
            }
            if (status.metadata.favoritedUsers.get(userRecord.InternalId)) {
                metadata.favoritedUsers.put(userRecord.InternalId, true)
            }
        }
        status.receivedUsers = receivedUsers
        status.metadata.favoritedUsers = metadata.favoritedUsers

        // 代表アカウントの再決定
        /*
         * 代表アカウントは次の優先順位で決定する。
         *
         * 1. Statusの所有者 (originStatus.user本人)
         * 2. 優先設定されたアカウント
         * 3. Mentionsで指名されたアカウント (Mentions内に所有するアカウントが複数ある場合、Mentions内でindexが一番若いアカウント)
         * 4. プライマリアカウント
         * 5. その他のアカウント
         *
         * このうち、1〜2については「高優先度判定」としてここでは取り扱わない。
         * (外部で決定し、その際にrepresentOverrodeフラグを設定することでマージ時の代表再決定をスキップする)
         *
         * 3〜5については、マージ対象となる2つのStatus双方のreceivedUsersを先にマージし、そこに含まれるアカウント間で決定する。
         */
        if (!representOverrode && status.representOverrode) {
            representOverrode = true
            representUser = status.representUser
        } else if (representOverrode && !status.representOverrode) {
            status.representOverrode = true
            status.representUser = representUser
        } else if (!representOverrode && !status.representOverrode) {
            // メンションを受けているアカウントがあれば、そのアカウントで上書き
            val mentioned = mentions.mapNotNull { mention -> receivedUsers.firstOrNull(mention::isMentionedTo) }.firstOrNull()
            if (mentioned != null) {
                representUser = mentioned
                status.representUser = mentioned
            } else {
                // 受信アカウントの中にプライマリアカウントがいれば、そのアカウントで上書き
                val primary = receivedUsers.firstOrNull { it.isPrimary }
                if (primary != null) {
                    representUser = primary
                    status.representUser = primary
                }
            }
        }

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

    fun getInReplyTo(): InReplyToId = InReplyToId(url.orEmpty())

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

    public override fun clone(): Status {
        val s = super.clone() as Status
        s.receivedUsers = ArrayList(receivedUsers)
        return s
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
