package shibafu.yukari.entity

import shibafu.yukari.media2.Media
import shibafu.yukari.database.AuthUserRecord
import java.io.Serializable
import java.util.*

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
    val recipientScreenName: String // TODO: たぶんいらない

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
    val favoritesCount: Int

    /**
     * 引用数
     */
    val repostsCount: Int

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
     * このステータスを受信したアカウント
     *
     * 通信先 [shibafu.yukari.database.Provider] が異なる同一内容のステータスがある状況では、共通情報・サーバーローカル情報の取り違いを避けるための判定にも使われる。
     * そのため、直接の通信先と関係の無いアカウントを返してはいけない。
     */
    val receiverUser: AuthUserRecord

    /**
     * このステータスを所有している受信アカウント
     *
     * 通常、ステータスを受信して [shibafu.yukari.linkage.TimelineHub] で配送される時に設定される。
     * このプロパティは [prioritizedUser] より優先して扱う必要がある。
     */
    var preferredOwnerUser: AuthUserRecord?

    /**
     * 操作用に優先設定されている受信アカウント
     *
     * [shibafu.yukari.database.UserExtras.priorityAccount] に基づいて設定される。
     */
    var prioritizedUser: AuthUserRecord?

    /**
     * 代表受信アカウント
     *
     * [preferredOwnerUser] および [prioritizedUser] を考慮して、操作用のアカウントを返す。
     * ステータスのサーバーローカル情報とは関係のないアカウントが返される可能性もあるため、APIリクエストの際には改めてIDの問い合わせが必要となる場合もある。
     */
    val representUser: AuthUserRecord
        get() {
            preferredOwnerUser?.let { return it }
            prioritizedUser?.let { return it }
            return receiverUser
        }

    /**
     * 同一のステータスを受信した全てのアカウント
     */
    val receivedUsers: List<AuthUserRecord>
        get() = sequence {
            preferredOwnerUser?.let { yield(it) }
            prioritizedUser?.let { yield(it) }
            yield(receiverUser)
        }.distinct().toList()

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
                preferredOwnerUser = userRecord
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
        return receivedUsers.map { it.InternalId }.any { metadata.favoritedUsers.contains(it) }
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
        return super.clone() as Status
    }

    fun getTextWithoutMentions(): String {
        var text = this.text
        mentions.forEach { mention ->
            text = text.replace("@${mention.screenName}", "")
        }
        return text.trim()
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
