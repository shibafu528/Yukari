package shibafu.yukari.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.R
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.util.AttrUtil

class DonStatusView : StatusView {
    private val visibilityUnlistedResId = AttrUtil.resolveAttribute(context.theme, R.attr.statusVisibilityUnlistedDrawable)
    private val visibilityPrivateResId = AttrUtil.resolveAttribute(context.theme, R.attr.statusVisibilityPrivateDrawable)
    private val visibilityDirectResId = AttrUtil.resolveAttribute(context.theme, R.attr.statusVisibilityDirectDrawable)

    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateIndicator() {
        super.updateIndicator()

        val status = status as DonStatus

        // 可視性アイコンの表示
        when (status.status.visibility) {
            Status.Visibility.Unlisted.value -> {
                ivProtected.setImageResource(visibilityUnlistedResId)
                ivProtected.visibility = View.VISIBLE
            }
            Status.Visibility.Private.value -> {
                ivProtected.setImageResource(visibilityPrivateResId)
                ivProtected.visibility = View.VISIBLE
            }
            Status.Visibility.Direct.value -> {
                ivProtected.setImageResource(visibilityDirectResId)
                ivProtected.visibility = View.VISIBLE
            }
            else ->
                ivProtected.visibility = View.GONE
        }
    }

    override fun decorateText(text: String): String {
        val status = status as DonStatus
        var decoratedText = text

        // Content Warningの適用
        if (!pref.getBoolean("pref_always_expand_cw", false)) {
            val originStatus = status.originStatus as DonStatus
            if (mode == Mode.DEFAULT && originStatus.status.spoilerText.isNotEmpty()) {
                decoratedText = originStatus.status.spoilerText + "\n...(content warning)..."
            }
        }

        return super.decorateText(decoratedText)
    }
}