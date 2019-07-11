package shibafu.yukari.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.View
import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.R
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.util.AttrUtil
import java.util.regex.Pattern

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
        val originStatus = status.originStatus as DonStatus

        // 可視性アイコンの表示
        when (originStatus.status.visibility) {
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

    override fun decorateTextSpan(text: String): Spannable {
        val status = status?.originStatus as DonStatus
        val spannable = SpannableString(text)

        // カスタム絵文字の出現部分を列挙
        val emojiRegions = HashMap<String, MutableList<IntRange>>(status.status.emojis.size)
        val matcher = PATTERN_EMOJI_SHORTCODE.matcher(text)
        if (matcher.find()) {
            do {
                val shortcode = matcher.group(1)

                if (emojiRegions[shortcode] == null) {
                    emojiRegions[shortcode] = arrayListOf()
                }

                emojiRegions[shortcode]!!.add(matcher.start()..matcher.end())
            } while (matcher.find())
        }

        // 表示対象の絵文字の情報を抽出
        val emojis = status.status.emojis.filter { emojiRegions.containsKey(it.shortcode) }
        if (emojis.isNotEmpty()) {
            emojis.forEach { emoji ->
                val regions = emojiRegions[emoji.shortcode] ?: return@forEach
                regions.forEach { range ->
                    val lineHeight = tvText.lineHeight

                    val cache = BitmapCache.getImage(emoji.staticUrl, BitmapCache.IMAGE_CACHE, context)
                    if (cache != null) {
                        val drawable = BitmapDrawable(context.resources, cache).apply {
                            setBounds(0, 0, lineHeight, lineHeight)
                        }

                        spannable.setSpan(ImageSpan(drawable), range.start, range.endInclusive, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        val drawable = ColorDrawable(Color.TRANSPARENT).apply {
                            setBounds(0, 0, lineHeight, lineHeight)
                        }

                        spannable.setSpan(ImageSpan(drawable), range.start, range.endInclusive, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                        ImageLoaderTask.loadDrawable(context, emoji.staticUrl, BitmapCache.IMAGE_CACHE) {
                            updateView()
                        }
                    }
                }
            }
        }

        return spannable
    }

    companion object {
        private val PATTERN_EMOJI_SHORTCODE = Pattern.compile(":([a-zA-Z0-9_]{2,}):")
    }
}