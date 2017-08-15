package shibafu.yukari.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import shibafu.yukari.R
import shibafu.yukari.activity.PreviewActivity
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.twitter.TweetCommon
import shibafu.yukari.twitter.TweetCommonDelegate
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusmanager.StatusManager
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.StringUtil
import java.util.*

/**
 * [PreformedStatus]を表示するためのビュー
 */
class TweetView : StatusView {
    // Delegate
    override val delegate: TweetCommonDelegate = TweetCommon.newInstance(PreformedStatus::class.java)

    // 背景リソースID
    private val bgRetweetResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetRetweet)

    // LayoutParams
    private val lpThumb: LinearLayout.LayoutParams

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        val attachPictureSize = context.resources.getDimensionPixelSize(R.dimen.attach_picture_size)
        lpThumb = LinearLayout.LayoutParams(attachPictureSize, attachPictureSize, 1f)
    }

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val status = status as PreformedStatus

        // ジオタグの表示
        if (status.originStatus.geoLocation != null) {
            val geoLocation = status.originStatus.geoLocation
            tvTimestamp.text = String.format("%s\nGeo: %f, %f",
                    tvTimestamp.text,
                    geoLocation.latitude,
                    geoLocation.longitude)
        }

        // サムネイルミュートされているか表示
        if (status.isCensoredThumbs) {
            tvTimestamp.text = "${tvTimestamp.text}\n[Thumbnail Muted]"
        }
    }

    override fun updateIndicator() {
        super.updateIndicator()

        val status = status as PreformedStatus

        // ふぁぼアイコンの表示
        if (status.originStatus.isFavoritedSomeone) {
            ivFavorited.visibility = View.VISIBLE
        } else {
            ivFavorited.visibility = View.GONE
        }

        // 受信アカウントカラーの設定
        ivAccountColor.setBackgroundColor(status.representUser.AccountColor)

        // ユーザーカラーラベルの設定
        val color = userExtras.firstOrNull { it.id == status.sourceUser.id }?.color ?: Color.TRANSPARENT
        ivUserColor.setBackgroundColor(color)
    }

    @SuppressLint("SetTextI18n")
    override fun updateDecoration() {
        super.updateDecoration()
        val status = status as PreformedStatus

        // 添付対応
        updateAttaches(status)

        // リツイート対応
        if (mode != Mode.PREVIEW) {
            if (status.isRetweet) {
                val timestamp = "RT by @" + status.user.screenName + "\n" +
                        StringUtil.formatDate(status.retweetedStatus.createdAt) + " via " + status.retweetedStatus.source
                tvTimestamp.text = timestamp
                tvName.text = "@" + status.retweetedStatus.user.screenName + " / " + status.retweetedStatus.user.name
                setBackgroundResource(bgRetweetResId)

                if (status.retweetedStatus.user.isProtected) {
                    ivProtected.visibility = View.VISIBLE
                } else {
                    ivProtected.visibility = View.INVISIBLE
                }

                ivRetweeterIcon.visibility = View.VISIBLE
                ImageLoaderTask.loadProfileIcon(context,
                        ivRetweeterIcon,
                        status.user.biggerProfileImageURLHttps)
            } else {
                ivRetweeterIcon.visibility = View.INVISIBLE
                ivRetweeterIcon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }

        // 引用対応
        when (mode) {
            Mode.DEFAULT, Mode.DETAIL -> {
                val quoteEntities = status.quoteEntities
                if (quoteEntities.size > 0) {
                    flInclude.removeAllViews()
                    flInclude.visibility = View.VISIBLE

                    quoteEntities.forEach { quoteId ->
                        if (StatusManager.getReceivedStatuses().get(quoteId) != null) {
                            // TODO: シングルライン対応は？
                            val tv = TweetView(context).also {
                                it.status = StatusManager.getReceivedStatuses().get(quoteId)
                                it.userRecords = userRecords
                                it.userExtras = userExtras
                                it.mode = mode or Mode.INCLUDE
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
        val status = status as PreformedStatus

        if (mode == Mode.DEFAULT && status.isTooManyRepeatText && pref.getBoolean("pref_shorten_repeat_text", false)) {
            decoratedText = status.repeatedSequence + "\n...(repeat)..."
        }

        return decoratedText
    }

    private fun updateAttaches(status: PreformedStatus) {
        if (pref.getBoolean("pref_prev_enable", true) && mode != Mode.PREVIEW || mode == Mode.DETAIL) {
            var hidden = false

            var selectedFlags = pref.getInt("pref_prev_time", 0)
            if (selectedFlags != 0) {
                val selectedStates = BooleanArray(24)
                for (i in 0..23) {
                    selectedStates[i] = selectedFlags and 0x01 == 1
                    selectedFlags = selectedFlags ushr 1
                }
                val calendar = Calendar.getInstance()
                hidden = selectedStates[calendar.get(Calendar.HOUR_OF_DAY)]
            }
            hidden = hidden or status.originStatus.isCensoredThumbs

            if (!hidden || pref.getBoolean("pref_prev_mosaic", false)) {
                val mediaList = status.originStatus.mediaLinkList
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
                                media.thumbURL,
                                BitmapCache.IMAGE_CACHE,
                                hidden && pref.getBoolean("pref_prev_mosaic", false))

                        if (mode and Mode.DETAIL == Mode.DETAIL && media.canPreview() || pref.getBoolean("pref_extended_touch_event", false)) {
                            iv.setOnClickListener { _ ->
                                val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(media.browseURL),
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