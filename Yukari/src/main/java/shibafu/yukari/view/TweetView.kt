package shibafu.yukari.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import shibafu.yukari.R
import shibafu.yukari.database.Bookmark
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.AttrUtil

/**
 * [TwitterStatus]を表示するためのビュー
 */
class TweetView : StatusView {
    private val visibilityPrivateResId = AttrUtil.resolveAttribute(context.theme, R.attr.statusVisibilityPrivateDrawable)

    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val status = (status as? Bookmark)?.twitterStatus ?: status as TwitterStatus

        // ジオタグの表示
        val originStatus = status.originStatus as TwitterStatus
        if (originStatus.status.geoLocation != null) {
            val geoLocation = originStatus.status.geoLocation
            tvTimestamp.text = String.format("Geo: %f, %f\n%s",
                    geoLocation.latitude,
                    geoLocation.longitude,
                    tvTimestamp.text)
        }
    }

    override fun updateIndicator() {
        super.updateIndicator()

        val status = (status as? Bookmark)?.twitterStatus ?: status as TwitterStatus

        // 鍵垢アイコンの表示
        if (status.originStatus.user.isProtected) {
            ivProtected.setImageResource(visibilityPrivateResId)
            ivProtected.visibility = View.VISIBLE
        } else {
            ivProtected.visibility = View.GONE
        }

        // ユーザーカラーラベルの設定
        val color = userExtras.firstOrNull { it.id == status.originStatus.user.identicalUrl }?.color ?: Color.TRANSPARENT
        ivUserColor.setBackgroundColor(color)
    }

    @SuppressLint("SetTextI18n")
    override fun updateDecoration() {
        super.updateDecoration()

        // ハイパーミュート
        if (pref.getBoolean("j_fullmute", false)) {
            hideTextViews()
        }
    }
}