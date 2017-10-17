package shibafu.yukari.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.preference.PreferenceManager
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
import shibafu.yukari.common.FontAsset
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.UserExtras
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TweetCommonDelegate
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.StringUtil
import twitter4j.TwitterResponse

/**
 * タイムラインの要素を表示するためのビューの基本部分
 */
abstract class StatusView : RelativeLayout {
    var status: TwitterResponse? = null
        set(value) {
            field = value
            updateView()
        }
    var userRecords: List<AuthUserRecord> = emptyList()
    var userExtras: List<UserExtras> = emptyList()
    var mode: Int = Mode.DEFAULT

    // EventListener
    var onTouchProfileImageIconListener: OnTouchProfileImageIconListener? = null

    // Delegate
    protected abstract val delegate: TweetCommonDelegate

    // SharedPref
    protected val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // SingleLine
    protected val singleLine: Boolean

    // 背景リソースID
    protected val bgDefaultResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetNormal)
    protected val bgMentionResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetMention)
    protected val bgOwnResId = AttrUtil.resolveAttribute(context.theme, R.attr.tweetOwn)

    // View
    protected val tvName: TextView by lazy { findViewById(R.id.tweet_name) as TextView }
    protected val tvText: TextView by lazy { findViewById(R.id.tweet_text) as TextView }
    protected val ivIcon: ImageView by lazy { findViewById(R.id.tweet_icon) as ImageView }
    protected val ivRetweeterIcon: ImageView by lazy { findViewById(R.id.tweet_retweeter) as ImageView }
    protected val ivProtected: ImageView by lazy { findViewById(R.id.tweet_protected) as ImageView }
    protected val ivFavorited: ImageView by lazy { findViewById(R.id.tweet_faved) as ImageView }
    protected val tvTimestamp: TextView by lazy { findViewById(R.id.tweet_timestamp) as TextView }
    protected val llAttach: LinearLayout by lazy { findViewById(R.id.tweet_attach) as LinearLayout }
    protected val tvReceived: TextView by lazy { findViewById(R.id.tweet_receive) as TextView }
    protected val flInclude: LinearLayout by lazy { findViewById(R.id.tweet_include) as LinearLayout }
    protected val ivAccountColor: ImageView by lazy { findViewById(R.id.tweet_accountcolor) as ImageView }
    protected val ivUserColor: ImageView by lazy { findViewById(R.id.tweet_color) as ImageView }

    constructor(context: Context?, singleLine: Boolean) : super(context, null, 0) {
        this.singleLine = singleLine
        initializeView(context, null, 0)
    }

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        singleLine = false
        initializeView(context, attrs, defStyleAttr)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        singleLine = false
        initializeView(context, attrs, defStyleAttr)
    }

    /**
     * 現在の状態で配下にあるViewの状態を更新する。
     */
    open fun updateView() {
        var fontSizeStr = pref.getString("pref_font_timeline", "14")
        if (fontSizeStr == "") {
            fontSizeStr = "14"
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
        val user = delegate.getUser(status)
        val displayName = if (pref.getBoolean("pref_remove_name_newline", false)) { user.name.replace("\n", "") } else { user.name }

        tvName.typeface = typeface
        tvName.textSize = fontSize
        tvName.text = "@${user.screenName} / $displayName"
    }

    /**
     * 本文欄の表示を更新する。
     */
    protected open fun updateText(typeface: Typeface, fontSize: Float) {
        tvText.typeface = typeface
        tvText.textSize = fontSize

        // ユーザー設定に応じたテキストの加工を行う
        val text = decorateText(delegate.getText(status))

        // ショート表示の場合はScreenNameと結合して表示、そうでなければそのまま表示
        if (pref.getBoolean("pref_mode_singleline", false) && Mode.DETAIL or Mode.PREVIEW and mode == 0) {
            val sb = SpannableStringBuilder()
            sb.append(delegate.getUser(status).screenName)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#ff419b38")), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" ")
            sb.append(text.replace("\n", " "))
            tvText.text = sb
        } else {
            tvText.text = text
        }
    }

    /**
     * タイムスタンプ欄の表示を更新する。
     */
    @SuppressLint("SetTextI18n")
    protected open fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        tvTimestamp.typeface = typeface
        tvTimestamp.textSize = fontSize * 0.8f
        tvTimestamp.text = StringUtil.formatDate(delegate.getCreatedAt(status)) + " via " + delegate.getSource(status)
    }

    /**
     * 受信者欄の表示を更新する。
     */
    protected open fun updateReceiverText(typeface: Typeface, fontSize: Float) {
        if (pref.getBoolean("pref_show_received", false)) {
            tvReceived.visibility = View.VISIBLE
            tvReceived.typeface = typeface
            tvReceived.textSize = fontSize * 0.8f
            tvReceived.text = String.format("Received from @%s", delegate.getRecipientScreenName(status))
        } else {
            tvReceived.visibility = View.GONE
        }
    }

    /**
     * アイコンの表示を更新する。
     */
    protected open fun updateIcon() {
        val user = delegate.getUser(status)

        val imageUrl = if (pref.getBoolean("pref_narrow", false))
                           user.profileImageURLHttps
                       else
                           user.biggerProfileImageURLHttps

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
        if (delegate.getUser(status).isProtected) {
            ivProtected.visibility = View.VISIBLE
        } else {
            ivProtected.visibility = View.GONE
        }
    }

    /**
     * 装飾的な部分のプロパティを更新する。
     */
    protected open fun updateDecoration() {
        // 背景リソースを設定
        val statusRelation = delegate.getStatusRelation(userRecords, status)
        if (mode != Mode.PREVIEW) {
            when (statusRelation) {
                TweetCommonDelegate.REL_MENTION -> setBackgroundResource(bgMentionResId)
                TweetCommonDelegate.REL_OWN -> setBackgroundResource(bgOwnResId)
                else -> setBackgroundResource(bgDefaultResId)
            }
        }

        // プレビューモードの場合は黒くする
        if (mode == Mode.PREVIEW) {
            tvName.setTextColor(Color.BLACK)
            tvTimestamp.setTextColor(Color.BLACK)
            tvReceived.setTextColor(Color.BLACK)
        }
    }

    /**
     * 本文欄のテキストの加工を行う。
     */
    protected open fun decorateText(text: String): String {
        val multilineMode = pref.getString("pref_mode_multiline", "0").toInt()

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
                    sb.append(it)
                }
                if (lines.size > limit) {
                    sb.append(" ...")
                }
                decoratedText = sb.toString()
            }
        }

        return decoratedText
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
        fun onTouch(element: TwitterResponse?, v: View, event: MotionEvent): Boolean
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

