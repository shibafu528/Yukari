package shibafu.yukari.entity

import java.util.*

/**
 * 途中読み込みマーカー
 * @param id 読み込みを開始する位置のID (maxId)
 * @param serviceId サービスを特定するためのID
 * @param anchorStatusId
 * @param userId 対応するアカウントのユーザID
 * @param loadMarkerTag 対応するクエリを表すタグ
 */
class LoadMarker(override val id: Long,
                 val serviceId: String,
                 val anchorStatusId: Long,
                 val userId: Long,
                 val loadMarkerTag: String) : Status {
    /** 現在このマーカーを使って通信を実行しているタスクのID */
    var taskKey = -1L

    override val user: User by lazy { object : User {
        override val id: Long = userId
        override val name: String = ""
        override val screenName: String = "**Load Marker**"
        override val isProtected: Boolean = false
        override val profileImageUrl: String = ""
        override val biggerProfileImageUrl: String = ""
    } }

    override val text: String
        get() = "Anchor ID = $anchorStatusId, SID = $serviceId, UID = $userId, Tag = $loadMarkerTag, TaskKey = $taskKey"

    override val recipientScreenName: String = "**Load Marker**"
    override val createdAt: Date = Date()
    override val source: String = ""
}