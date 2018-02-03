package shibafu.yukari.entity

import shibafu.yukari.twitter.AuthUserRecord
import java.util.*

/**
 * 途中読み込みマーカー
 * @param id 読み込みを開始する位置のID (maxId)
 * @param providerApiType 対応する [shibafu.yukari.database.Provider.apiType] の値
 * @param anchorStatusId
 * @param representUser 対応するアカウント
 * @param loadMarkerTag 対応するクエリを表すタグ
 */
class LoadMarker(override val id: Long,
                 override val providerApiType: Int,
                 val anchorStatusId: Long,
                 override var representUser: AuthUserRecord,
                 val loadMarkerTag: String) : Status {
    /** 現在このマーカーを使って通信を実行しているタスクのID */
    var taskKey = -1L

    override val user: User by lazy { object : User {
        override val id: Long = representUser.NumericId
        override val name: String = ""
        override val screenName: String = "**Load Marker**"
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
    } }

    override val text: String
        get() = "Anchor ID = $anchorStatusId, API = $providerApiType, UID = ${representUser.NumericId}, Tag = $loadMarkerTag, TaskKey = $taskKey"

    override val recipientScreenName: String = "**Load Marker**"
    override val createdAt: Date = Date()
    override val source: String = ""
    override val metadata: StatusPreforms = StatusPreforms()
    override var receivedUsers: MutableList<AuthUserRecord> = arrayListOf(representUser)
}