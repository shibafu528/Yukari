package shibafu.yukari.entity

import shibafu.yukari.database.AuthUserRecord
import java.util.*

/**
 * 途中読み込みマーカー
 * @param id 読み込みを開始する位置のID (maxId)
 * @param providerApiType 対応する [shibafu.yukari.database.Provider.apiType] の値
 * @param anchorStatusId 先行するステータスのID
 * @param receiverUser 対応するアカウント
 * @param loadMarkerTag 対応するクエリを表すタグ
 * @param createdAt タイムスタンプ
 * @param stringCursor ページング追加情報
 * @param longCursor ページング追加情報
 */
class LoadMarker(override val id: Long,
                 override val providerApiType: Int,
                 val anchorStatusId: Long,
                 override val receiverUser: AuthUserRecord,
                 val loadMarkerTag: String,
                 override val createdAt: Date,
                 val stringCursor: String = "",
                 val longCursor: Long = 0) : Status {
    /** 現在このマーカーを使って通信を実行しているタスクのID */
    var taskKey = -1L

    override val user: User = object : User {
        override val id: Long = receiverUser.NumericId
        override val name: String = ""
        override val screenName: String = "**Load Marker**"
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
        override fun isMentionedTo(userRecord: AuthUserRecord): Boolean = false
    }

    override val text: String
        get() = "Anchor ID = $anchorStatusId, API = $providerApiType, UID = ${receiverUser.NumericId}, Tag = $loadMarkerTag, TaskKey = $taskKey"

    override val recipientScreenName: String = "**Load Marker**"
    override val source: String = ""
    override val favoritesCount: Int = 0
    override val repostsCount: Int = 0
    override val metadata: StatusPreforms = StatusPreforms()
    override val providerHost: String = HOST
    override var preferredOwnerUser: AuthUserRecord? = null
    override var prioritizedUser: AuthUserRecord? = null

    companion object {
        const val HOST = "load-marker.yukari.internal"
    }
}