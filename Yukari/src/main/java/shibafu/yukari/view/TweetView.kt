package shibafu.yukari.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import shibafu.yukari.R
import shibafu.yukari.activity.PreviewActivity
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.Provider
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.twitter.entity.TwitterStatus
import java.util.*

/**
 * [TwitterStatus]を表示するためのビュー
 */
class TweetView : StatusView {
    // LayoutParams
    private val lpThumb: LinearLayout.LayoutParams

    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        val attachPictureSize = context.resources.getDimensionPixelSize(R.dimen.attach_picture_size)
        lpThumb = LinearLayout.LayoutParams(attachPictureSize, attachPictureSize, 1f)
    }

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

        // 添付対応
        updateAttaches(status)

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

    private fun updateAttaches(status: TwitterStatus) {
        if (pref.getBoolean("pref_prev_enable", true) && mode != Mode.PREVIEW || mode == Mode.DETAIL) {
            var hidden = false

            var selectedFlags = pref.getInt("pref_prev_time", 0)
            if (selectedFlags != 0) {
                // 31 | .... .... 0000 0000 0000 0000 0000 0000 | 0
                //                `23:00-59    `12:00-59      `00:00-59
                hidden = selectedFlags and (1 shl Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) != 0
            }
            hidden = hidden or status.originStatus.metadata.isCensoredThumbs

            if (!hidden || pref.getBoolean("pref_prev_mosaic", false)) {
                val mediaList = status.originStatus.media
                val mlSize = mediaList.size
                if (mlSize > 0) {
                    llAttach.visibility = View.VISIBLE
                    var iv: ImageView?
                    var i: Int = 0
                    while (i < mlSize) {
                        val media = mediaList[i]
                        iv = llAttach.findViewById(i) as ImageView?
                        if (iv == null) {
                            iv = ImageView(context)
                            iv.id = i
                            llAttach.addView(iv, lpThumb)
                        } else {
                            iv.visibility = View.VISIBLE
                        }
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        ImageLoaderTask.loadBitmap(context,
                                iv,
                                media,
                                ImageLoaderTask.RESOLVE_THUMBNAIL,
                                BitmapCache.IMAGE_CACHE,
                                hidden && pref.getBoolean("pref_prev_mosaic", false))

                        if (mode and Mode.DETAIL == Mode.DETAIL && media.canPreview() || pref.getBoolean("pref_extended_touch_event", false)) {
                            iv.setOnClickListener { _ ->
                                val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(media.browseUrl),
                                        context,
                                        PreviewActivity::class.java)
                                intent.putExtra(PreviewActivity.EXTRA_STATUS, status)
                                context.startActivity(intent)
                            }
                        }
                        ++i
                    }
                    //使ってない分はしまっちゃおうね
                    val childCount = llAttach.childCount
                    if (i < childCount) {
                        while (i < childCount) {
                            llAttach.findViewById(i).visibility = View.GONE
                            ++i
                        }
                    }
                } else {
                    llAttach.visibility = View.GONE
                }
            } else {
                llAttach.visibility = View.GONE
            }
        } else {
            llAttach.visibility = View.GONE
        }
    }

}