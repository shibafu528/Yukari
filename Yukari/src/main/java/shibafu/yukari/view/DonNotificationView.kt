package shibafu.yukari.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.sys1yagi.mastodon4j.api.entity.Notification
import shibafu.yukari.mastodon.entity.DonNotification
import shibafu.yukari.util.StringUtil

/**
 * [DonNotification]を表示するためのビュー
 */
class DonNotificationView : StatusView {
    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateName(typeface: Typeface, fontSize: Float) {
        super.updateName(typeface, fontSize)

        val notification = notification
        val kind = when (notification.notification.type) {
            Notification.Type.Mention.value -> "返信" // NOTE: 返信は直接DonStatusViewに表示するため、DonNotificationViewは使わない
            Notification.Type.Reblog.value -> "ブースト"
            Notification.Type.Favourite.value -> "お気に入り登録"
            Notification.Type.Follow.value -> "フォロー"
            "follow_request" -> "フォローリクエスト"
            "status" -> "投稿"
            "poll" -> "作成した投票が終了"
            "update" -> "投稿を更新"
            else -> notification.notification.type
        }

        tvName.text = String.format("@%sさんが%s", notification.user.screenName, kind)
    }

    override fun updateText(typeface: Typeface, fontSize: Float) {
        tvText.visibility = View.GONE
    }

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val notification = notification

        tvTimestamp.text = StringUtil.formatDate(notification.createdAt)
    }

    override fun updateDecoration() {
        super.updateDecoration()

        val notification = notification

        if (notification.status == null) {
            flInclude.visibility = View.GONE
        } else {
            flInclude.visibility = View.VISIBLE
            val include = if (flInclude.childCount == 0) {
                DonStatusView(context).also {
                    it.userRecords = userRecords
                    it.userExtras = userExtras
                    it.mode = mode or Mode.INCLUDE
                    flInclude.addView(it)
                }
            } else {
                flInclude.getChildAt(0) as StatusView
            }
            include.status = notification.status
        }
    }

    private val notification: DonNotification
        get() = status as DonNotification
}