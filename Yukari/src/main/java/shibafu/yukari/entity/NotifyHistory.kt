package shibafu.yukari.entity

import android.support.annotation.IntDef
import java.util.*

/**
 * 通知履歴
 */
class NotifyHistory(timeAtMillis: Long, @Kind val kind: Int, eventBy: User, val status: Status) : Status {
    override val id: Long = timeAtMillis
    override val user: User = eventBy
    override val text: String = ""
    override val recipientScreenName: String = ""
    override val createdAt: Date by lazy { Date(timeAtMillis) }
    override val source: String = ""

    companion object {
        const val KIND_FAVED = 0L
        const val KIND_RETWEETED = 1L
    }

    @IntDef(KIND_FAVED, KIND_RETWEETED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Kind
}