package shibafu.yukari.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import shibafu.yukari.twitter.TweetCommon
import shibafu.yukari.twitter.TweetCommonDelegate
import shibafu.yukari.twitter.statusimpl.HistoryStatus
import shibafu.yukari.util.StringUtil

class HistoryView : StatusView {
    // Delegate
    override val delegate: TweetCommonDelegate = TweetCommon.newInstance(HistoryStatus::class.java)

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateName(typeface: Typeface, fontSize: Float) {
        super.updateName(typeface, fontSize)

        val historyStatus = status as HistoryStatus

        tvName.text = String.format("@%sさんが%s", historyStatus.user.screenName, historyStatus.kindString)
    }

    override fun updateText(typeface: Typeface, fontSize: Float) {
        tvText.visibility = View.GONE
    }

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val historyStatus = status as HistoryStatus

        tvTimestamp.text = StringUtil.formatDate(historyStatus.createdAt)
    }

    override fun updateDecoration() {
        super.updateDecoration()

        val historyStatus = status as HistoryStatus

        flInclude.visibility = View.VISIBLE
        val include = if (flInclude.childCount == 0) {
            TweetView(context).also {
                it.userRecords = userRecords
                it.userExtras = userExtras
                it.mode = mode or Mode.INCLUDE
                flInclude.addView(it)
            }
        } else {
            flInclude.getChildAt(0) as TweetView
        }
        include.status = historyStatus.status
    }

    val HistoryStatus.kindString: String
        get() {
            return when (kind) {
                HistoryStatus.KIND_FAVED -> "お気に入り登録"
                HistoryStatus.KIND_RETWEETED -> "リツイート"
                else -> "反応"
            }
        }
}