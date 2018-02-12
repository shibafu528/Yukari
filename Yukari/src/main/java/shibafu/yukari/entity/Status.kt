package shibafu.yukari.entity

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
     * メッセージの発信者
     *
     * 実装にあたっては、このメソッドで取得できる情報は必ず **元の発信者** である必要がある。
     * 例えばTwitterの場合、RT StatusではRTされた人の情報が取得できるようにする。
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
     * 返信先の一覧
     */
    val mentions: List<Mention>

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
            if (providerApiType == userRecord.Provider.apiType && user.id == userRecord.NumericId) {
                representUser = userRecord
                if (!receivedUsers.contains(userRecord)) {
                    receivedUsers.add(userRecord)
                }
                return
            }
        }
    }

    override fun compareTo(other: Status): Int {
        if (this === other) return 0

        if (this.javaClass == other.javaClass) {
            // 同一型の場合はIDが時系列であると信用して処理
            if (this.id == other.id) return 0
            if (this.id < other.id) return -1
            return 1
        } else {
            // 型が異なる場合は、タイムスタンプで比較
            if (this.createdAt == other.createdAt) {
                val thisHash = this.hashCode()
                val otherHash = other.hashCode()

                if (thisHash == otherHash) throw RuntimeException()
                if (thisHash < otherHash) return -1
                return 1
            }
            if (this.createdAt < other.createdAt) return -1
            return 1
        }
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