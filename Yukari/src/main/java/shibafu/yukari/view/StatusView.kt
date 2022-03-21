package shibafu.yukari.view

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import shibafu.yukari.R
import shibafu.yukari.activity.PreviewActivity2
import shibafu.yukari.common.FontAsset
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.UserExtras
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.TimelineHubImpl
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.StringUtil
import java.util.*

/**
 * タイムラインの要素を表示するためのビューの基本部分
 */
abstract class StatusView : RelativeLayout {
    var status: Status? = null
        set(value) {
            field = value
            updateView()
        }
    var userRecords: List<AuthUserRecord> = emptyList()
    var userExtras: List<UserExtras> = emptyList()
    var mode: Int = Mode.DEFAULT

    // EventListener
    var onTouchProfileImageIconListener: OnTouchProfileImageIconListener? = null

    // SharedPref
    protected val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // SingleLine
    protected val singleLine: Boolean

    // 背景リソースID
    protected val bgDefaultResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetNormal)
    protected val bgMentionResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetMention)
    protected val bgRetweetResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetRetweet)
    protected val bgOwnResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetOwn)

    // View
    protected val tvName: TextView by lazy { findViewById<TextView>(R.id.tweet_name) }
    protected val tvText: TextView by lazy { findViewById<TextView>(R.id.tweet_text) }
    protected val ivIcon: ImageView by lazy { findViewById<ImageView>(R.id.tweet_icon) }
    protected val ivRetweeterIcon: ImageView by lazy { findViewById<ImageView>(R.id.tweet_retweeter) }
    protected val ivProtected: ImageView by lazy { findViewById<ImageView>(R.id.tweet_protected) }
    protected val ivFavorited: ImageView by lazy { findViewById<ImageView>(R.id.tweet_faved) }
    protected val tvTimestamp: TextView by lazy { findViewById<TextView>(R.id.tweet_timestamp) }
    protected val llAttach: LinearLayout by lazy { findViewById<LinearLayout>(R.id.tweet_attach) }
    protected val tvReceived: TextView by lazy { findViewById<TextView>(R.id.tweet_receive) }
    protected val flInclude: LinearLayout by lazy { findViewById<LinearLayout>(R.id.tweet_include) }
    protected val ivAccountColor: ImageView by lazy { findViewById<ImageView>(R.id.tweet_accountcolor) }
    protected val ivUserColor: ImageView by lazy { findViewById<ImageView>(R.id.tweet_color) }

    // LayoutParams
    private val lpThumb: LinearLayout.LayoutParams

    init {
        val attachPictureSize = context.resources.getDimensionPixelSize(R.dimen.attach_picture_size)
        lpThumb = LinearLayout.LayoutParams(attachPictureSize, attachPictureSize, 1f)
    }

    constructor(context: Context?, singleLine: Boolean) : super(context, null, 0) {
        this.singleLine = singleLine
        initializeView(context, null, 0)
    }

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        singleLine = false
        initializeView(context, attrs, defStyleAttr)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        singleLine = false
        initializeView(context, attrs, defStyleAttr)
    }

    /**
     * 現在の状態で配下にあるViewの状態を更新する。
     */
    open fun updateView() {
        var fontSizeStr = pref.getString("pref_font_timeline", "11")
        if (fontSizeStr == "") {
            fontSizeStr = "11"
        }
        val fontSize = fontSizeStr.toFloat()
        val typeface = FontAsset.getInstance(context).font

        updateName(typeface, fontSize)
        updateText(typeface, fontSize)
        updateTimestamp(typeface, fontSize)
        updateReceiverText(typeface, fontSize)
        updateIndicator()
        updateIcon()

        updateDecoration()
    }

    /**
     * 名前欄の表示を更新する。
     */
    protected open fun updateName(typeface: Typeface, fontSize: Float) {
        val status = status ?: return

        val user = status.originStatus.user
        val screenName = if (pref.getBoolean("pref_compress_acct", false)) { MastodonUtil.compressAcct(user.screenName) } else { user.screenName }
        val displayName = if (pref.getBoolean("pref_remove_name_newline", false)) { user.name.replace("\n", "") } else { user.name }

        tvName.typeface = typeface
        tvName.textSize = fontSize
        tvName.text = "@$screenName / $displayName"
    }

    /**
     * 本文欄の表示を更新する。
     */
    protected open fun updateText(typeface: Typeface, fontSize: Float) {
        val status = status ?: return

        tvText.typeface = typeface
        tvText.textSize = fontSize

        // ユーザー設定に応じたテキストの加工を行う
        val text = decorateText(status.text)

        // ショート表示の場合はScreenNameと結合して表示、そうでなければそのまま表示
        if (pref.getBoolean("pref_mode_singleline", false) && Mode.DETAIL or Mode.PREVIEW and mode == 0) {
            val sb = SpannableStringBuilder()
            sb.append(status.originStatus.user.screenName)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#ff419b38")), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" ")
            sb.append(decorateTextSpan(text.replace("\n", " ")))
            tvText.text = sb
        } else {
            tvText.text = decorateTextSpan(text)
        }
    }

    /**
     * タイムスタンプ欄の表示を更新する。
     */
    @SuppressLint("SetTextI18n")
    protected open fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        val status = status ?: return

        tvTimestamp.typeface = typeface
        tvTimestamp.textSize = fontSize * 0.8f
        tvTimestamp.text = buildString {
            if (mode != Mode.PREVIEW && status.isRepost) {
                append("RT by @")
                append(status.user.screenName)
                append("\n")
            }

            append(StringUtil.formatDate(status.originStatus.createdAt))
            if (status.originStatus.source.isNotEmpty()) {
                append(" via ")
                append(status.originStatus.source)
            }

            if (status.metadata.isCensoredThumbs) {
                append("\n")
                append("[Thumbnail Muted]")
            }
        }
    }

    /**
     * 受信者欄の表示を更新する。
     */
    protected open fun updateReceiverText(typeface: Typeface, fontSize: Float) {
        val status = status ?: return

        if (pref.getBoolean("pref_show_received", false)) {
            tvReceived.visibility = View.VISIBLE
            tvReceived.typeface = typeface
            tvReceived.textSize = fontSize * 0.8f
            tvReceived.text = String.format("Received from @%s", status.recipientScreenName)
        } else {
            tvReceived.visibility = View.GONE
        }
    }

    /**
     * アイコンの表示を更新する。
     */
    protected open fun updateIcon() {
        val status = status ?: return
        val user = status.originStatus.user

        val imageUrl = if (pref.getBoolean("pref_narrow", false))
                           user.profileImageUrl
                       else
                           user.biggerProfileImageUrl

        if (ivIcon.tag == null || ivIcon.tag != imageUrl) {
            ImageLoaderTask.loadProfileIcon(context, ivIcon, imageUrl)
        }

        val onTouchProfileImageIconListener = onTouchProfileImageIconListener
        if (onTouchProfileImageIconListener != null) {
            ivIcon.setOnTouchListener({ _, event -> onTouchProfileImageIconListener.onTouch(status, this, event) })
        }
    }

    /**
     * インジケーターの表示を更新する。
     */
    protected open fun updateIndicator() {
        val status = status ?: return

        // 受信アカウントカラーの設定
        ivAccountColor.setBackgroundColor(status.representUser.AccountColor)

        // ふぁぼアイコンの表示
        if (status.originStatus.isFavoritedSomeone()) {
            ivFavorited.visibility = View.VISIBLE
        } else {
            ivFavorited.visibility = View.GONE
        }
    }

    /**
     * 装飾的な部分のプロパティを更新する。
     */
    protected open fun updateDecoration() {
        val status = status ?: return

        // 背景リソースを設定
        val statusRelation = status.getStatusRelation(userRecords)
        if (mode != Mode.PREVIEW) {
            when (statusRelation) {
                Status.RELATION_MENTIONED_TO_ME -> setBackgroundResource(bgMentionResId)
                Status.RELATION_OWNED -> setBackgroundResource(bgOwnResId)
                else -> setBackgroundResource(bgDefaultResId)
            }
        }

        // プレビューモードの場合は黒くする
        if (mode == Mode.PREVIEW) {
            tvName.setTextColor(Color.BLACK)
            tvTimestamp.setTextColor(Color.BLACK)
            tvReceived.setTextColor(Color.BLACK)
        }

        // リツイート対応
        if (mode != Mode.PREVIEW) {
            if (status.isRepost) {
                setBackgroundResource(bgRetweetResId)

                ivRetweeterIcon.visibility = View.VISIBLE
                ImageLoaderTask.loadProfileIcon(context,
                        ivRetweeterIcon,
                        status.user.biggerProfileImageUrl)
            } else {
                ivRetweeterIcon.visibility = View.INVISIBLE
                ivRetweeterIcon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }

        // 添付対応
        updateAttaches(status)

        // 引用対応
        when (mode) {
            Mode.DEFAULT, Mode.DETAIL -> {
                if (status.links.isNotEmpty()) {
                    flInclude.removeAllViews()
                    flInclude.visibility = View.VISIBLE

                    status.links.forEach { url ->
                        val quotedStatus = TimelineHubImpl.findStatusByUrl(url)

                        // TODO: あぁ〜うまいことやりてえ。なんでFactoryになってないの。
                        val sv = when (quotedStatus) {
                            is TwitterStatus -> TweetView(context, singleLine)
                            is DonStatus -> DonStatusView(context, singleLine)
                            else -> return@forEach
                        }

                        sv.userRecords = userRecords
                        sv.userExtras = userExtras
                        sv.mode = mode or Mode.INCLUDE
                        sv.status = quotedStatus

                        flInclude.addView(sv)
                    }
                } else {
                    flInclude.visibility = View.GONE
                }
            }
            else -> flInclude.visibility = View.GONE
        }
    }

    /**
     * 本文欄のテキストの加工を行う。
     */
    protected open fun decorateText(text: String): String {
        val status = status ?: return text
        val multilineMode = pref.getString("pref_mode_multiline", "0").toInt()
        val shortenRepeatText = pref.getBoolean("pref_shorten_repeat_text", false)

        var decoratedText = text

        // 大草原モードの適用
        if (pref.getBoolean("j_grassleaf", false)) {
            decoratedText = decoratedText.replace(REGEX_KUSA, "wwwwwwwwwwwwwwwwwwwwwwwwwww")
                    .replace(REGEX_QUESTION, "？wwwwwwwwwwwwwwwwwwww")
                    .replace(REGEX_DASH, "＾〜〜〜〜wwwwwwwwwww")
            tvText.setTextColor(Color.parseColor("#0b5b12"))
        }

        // セミモードの適用
        if (pref.getBoolean("j_cicada", false)) {
            decoratedText = SEMI_CRY[Math.abs(decoratedText.hashCode()) % SEMI_CRY.size]
        }

        // 複数行ツイートの表示設定を適用
        if (mode == Mode.DEFAULT) {
            // 単行表示
            if (multilineMode == Config.OMISSION_RETURNS) {
                decoratedText = decoratedText.replace('\n', ' ')
            }
            // 省略表示
            if (multilineMode == Config.OMISSION_AFTER_4 || multilineMode == Config.OMISSION_AFTER_8) {
                val lines = decoratedText.lines()
                val sb = StringBuilder()
                val limit = if (multilineMode == Config.OMISSION_AFTER_4) 3 else 7
                lines.take(limit).forEach {
                    if (sb.isNotEmpty()) {
                        sb.append("\n")
                    }
                    sb.append(it)
                }
                if (lines.size > limit) {
                    sb.append(" ...")
                }
                decoratedText = sb.toString()
            }
            // 反復圧縮
            if (status.metadata.isTooManyRepeatText && shortenRepeatText) {
                decoratedText = status.metadata.repeatedSequence + "\n...(repeat)..."
            }
        }

        return decoratedText
    }

    /**
     * 本文欄のテキストをSpannableとして加工する。絵文字などの処理に使う。
     */
    protected open fun decorateTextSpan(text: String): Spannable {
        return SpannableString(text)
    }

    /**
     * 配下にある全てのTextViewを非表示にする。ハイパーミュート機能のためだけに存在している。
     */
    protected fun hideTextViews() {
        listOf(tvName, tvText, tvTimestamp, tvReceived).forEach {
            it.visibility = View.GONE
        }
    }

    /**
     * 共通の初期化処理。
     */
    private fun initializeView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) {
        if (singleLine) {
            LayoutInflater.from(context).inflate(R.layout.row_tweet_single, this)
        } else {
            LayoutInflater.from(context).inflate(R.layout.row_tweet, this)
        }
    }

    /**
     * 添付画像のサムネイル表示処理。
     */
    private fun updateAttaches(status: Status) {
        if (pref.getBoolean("pref_prev_enable", true) && mode != Mode.PREVIEW || mode == Mode.DETAIL) {
            var hidden = false

            var selectedFlags = pref.getInt("pref_prev_time", 0)
            if (selectedFlags != 0) {
                // 31 | .... .... 0000 0000 0000 0000 0000 0000 | 0
                //                `23:00-59    `12:00-59      `00:00-59
                hidden = selectedFlags and (1 shl Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) != 0
            }
            if (!pref.getBoolean("pref_prev_ignore_censored", false)) {
                hidden = hidden or status.originStatus.metadata.isCensoredThumbs
            }

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

                        if ((mode and Mode.DETAIL == Mode.DETAIL || pref.getBoolean("pref_extended_touch_event", false)) && media.canPreview()) {
                            iv.setOnClickListener { _ ->
                                val intent = PreviewActivity2.newIntent(context, Uri.parse(media.browseUrl), status,
                                        collection = mediaList.filter { it.canPreview() }.map { Uri.parse(it.browseUrl) })
                                context.startActivity(intent)
                            }
                        }
                        ++i
                    }
                    //使ってない分はしまっちゃおうね
                    val childCount = llAttach.childCount
                    if (i < childCount) {
                        while (i < childCount) {
                            llAttach.findViewById<View>(i).visibility = View.GONE
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

    object Config {
        const val OMISSION_AFTER_4  = 0x010 // 4行目以降を省略
        const val OMISSION_AFTER_8  = 0x020 // 8行目以降を省略
        const val OMISSION_RETURNS  = 0x040 // 単行表示
    }

    object Mode {
        const val DEFAULT = 0
        const val DETAIL  = 1   // サムネイル表示強制
        const val PREVIEW = 2   // サムネイル非表示強制、モノクロ
        const val INCLUDE = 128 // 引用モード
    }

    interface OnTouchProfileImageIconListener {
        fun onTouch(element: Status?, v: View, event: MotionEvent): Boolean
    }

    companion object {
        /** 大草原モード用パターンその1 */
        private val REGEX_KUSA = "(wwww|ｗｗ|。|\\.\\.\\.|…|・・・)".toRegex()
        /** 大草原モード用パターンその2 */
        private val REGEX_QUESTION = "[？?]".toRegex()
        /** 大草原モード用パターンその3 */
        private val REGEX_DASH = "[^＾][~〜]+".toRegex()

        /** セミモード専用の本文置換用テキスト。安部菜々ではない。 */
        private val SEMI_CRY = arrayOf(
                "ｼﾞｼﾞ…ｼﾞｼﾞｼﾞｼﾞ…………ﾐﾐﾐﾐﾐﾐﾝﾐﾝﾐﾝ!!!",
                "ミーーーン↑↑ｗｗｗｗｗミンミンミンミン↑↑ｗｗｗｗｗｗｗｗミーーーン↓↓ｗｗｗｗｗ",
                "ｱーーｼｬｯｼｬｯｼｬｯｼｬｯｼｬｯﾝﾎﾞｫｵｵーーｼｯ\nﾂｸﾂｸﾎﾞｫｵｵーｼｯ　ﾂｸﾂｸﾎﾞｫｵｵーｼｯ\nﾂｸﾂｸﾎﾞｫｵｵーｼｯ　ﾂｸﾂｸﾎﾞｫｵｵーｼｯ\nﾂｸﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ\nｳｨｨｨｲｲｲｲｲｲｨｨｨｨｨｨーー……",
                "ｼﾞｰｰｰｰｰｰｰｰｰｰｰﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼ\nｼﾞｰｰｰｰｰｰｰｰｰｰｰﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼ\nｼｰｰｰｰｰｰｰｰｰｰｰ"
        )
    }
}

