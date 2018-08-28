package shibafu.yukari.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.mastodon.entity.DonStatus

class DonStatusView : StatusView {
    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateIndicator() {
        super.updateIndicator()

        val status = status as DonStatus

        // 可視性アイコンの表示
        // TODO: とりあえずTwitter用のリソースが鍵くらいしかないので一律でそれを出してるけど、リソース揃えなおしたい
        when (status.status.visibility) {
            Status.Visibility.Private.value, Status.Visibility.Direct.value ->
                ivProtected.visibility = View.VISIBLE
            else ->
                ivProtected.visibility = View.GONE
        }
    }
}