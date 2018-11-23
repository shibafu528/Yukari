package shibafu.yukari.view

import android.content.Context
import android.os.Build
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.widget.CircularProgressDrawable
import android.util.AttributeSet
import android.widget.ProgressBar
import shibafu.yukari.R
import shibafu.yukari.util.AttrUtil

/**
 * Original: [https://stackoverflow.com/a/50336935]
 */
class MaterialProgressBar : ProgressBar {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val metrics = resources.displayMetrics
            val screenDensity = metrics.density
            val colorPrimaryResId = AttrUtil.resolveAttribute(context.theme, R.attr.colorAccent)

            val drawable = CircularProgressDrawable(context)
            drawable.setColorSchemeColors(ResourcesCompat.getColor(resources, colorPrimaryResId, context.theme))
            drawable.centerRadius = width / 2f
            drawable.strokeWidth = WIDTH_DP * screenDensity
            indeterminateDrawable = drawable
        }
    }

    companion object {
        private const val WIDTH_DP = 5
    }
}