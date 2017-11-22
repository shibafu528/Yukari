package shibafu.yukari.entity

import java.util.*

/**
 * 途中読み込みマーカー
 */
class LoadMarker(override val id: Long,
                 val serviceId: String,
                 val anchorStatusId: Long,
                 val userId: Long,
                 val loadMarkerTag: String) : Status {
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