package shibafu.yukari.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import shibafu.yukari.database.Provider
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.twitter.entity.TwitterStatus

/**
 * [TwitterStatus]を表示するためのビュー
 */
class TweetView : StatusView {
    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val status = status as TwitterStatus

        // ジオタグの表示
        val originStatus = status.originStatus as TwitterStatus
        if (originStatus.status.geoLocation != null) {
            val geoLocation = originStatus.status.geoLocation
            tvTimestamp.text = String.format("%s\nGeo: %f, %f",
                    tvTimestamp.text,
                    geoLocation.latitude,
                    geoLocation.longitude)
        }

        // サムネイルミュートされているか表示
        if (status.metadata.isCensoredThumbs) {
            tvTimestamp.text = "${tvTimestamp.text}\n[Thumbnail Muted]"
        }
    }

    override fun updateIndicator() {
        super.updateIndicator()

        val status = status as TwitterStatus

        // ふぁぼアイコンの表示
        if (status.originStatus.isFavoritedSomeone()) {
            ivFavorited.visibility = View.VISIBLE
        } else {
            ivFavorited.visibility = View.GONE
        }

        // ユーザーカラーラベルの設定
        val color = userExtras.firstOrNull { it.id == status.originStatus.user.id }?.color ?: Color.TRANSPARENT
        ivUserColor.setBackgroundColor(color)
    }

    @SuppressLint("SetTextI18n")
    override fun updateDecoration() {
        super.updateDecoration()
        val status = status as TwitterStatus

        // 引用対応
        when (mode) {
            Mode.DEFAULT, Mode.DETAIL -> {
                val quoteEntities = status.quoteEntities
                if (quoteEntities.size() > 0) {
                    flInclude.removeAllViews()
                    flInclude.visibility = View.VISIBLE

                    quoteEntities.forEach { quoteId ->
                        val receivedStatus = TimelineHub.getProviderLocalCache(Provider.TWITTER.id).receivedStatus.get(quoteId)
                        if (receivedStatus != null) {
                            val tv = TweetView(context, singleLine).also {
                                it.userRecords = userRecords
                                it.userExtras = userExtras
                                it.mode = mode or Mode.INCLUDE
                                it.status = receivedStatus
                            }
                            flInclude.addView(tv)
                        }
                    }
                } else {
                    flInclude.visibility = View.GONE
                }
            }
            else -> flInclude.visibility = View.GONE
        }

        // ハイパーミュート
        if (pref.getBoolean("j_fullmute", false)) {
            hideTextViews()
        }
    }

    override fun decorateText(text: String): String {
        var decoratedText = super.decorateText(text)
        val status = status as TwitterStatus

        if (mode == Mode.DEFAULT && status.metadata.isTooManyRepeatText && pref.getBoolean("pref_shorten_repeat_text", false)) {
            decoratedText = status.metadata.repeatedSequence + "\n...(repeat)..."
        }

        return decoratedText
    }
}