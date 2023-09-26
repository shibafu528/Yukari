package shibafu.yukari.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.util.StringUtil

/**
 * Historyタブの[NotifyHistory]を表示するためのビュー
 */
class HistoryView : StatusView {
    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateName(typeface: Typeface, fontSize: Float) {
        super.updateName(typeface, fontSize)

        val history = status as NotifyHistory

        tvName.text = String.format("@%sさんが%s", history.user.screenName, history.kindString)
    }

    override fun updateText(typeface: Typeface, fontSize: Float) {
        tvText.visibility = View.GONE
    }

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val history = status as NotifyHistory

        tvTimestamp.text = StringUtil.formatDate(history.createdAt)
    }

    override fun updateDecoration() {
        super.updateDecoration()

        val history = status as NotifyHistory

        flInclude.visibility = View.VISIBLE

        // 既に存在するViewが流用不能な場合は破棄
        if (flInclude.childCount > 0) {
            val include = flInclude.getChildAt(0) as StatusView
            val status = include.status
            if (status == null || status.providerApiType != history.status.providerApiType) {
                flInclude.removeAllViews()
            }
        }

        val include = if (flInclude.childCount == 0) {
            when (history.status.providerApiType) {
                Provider.API_TWITTER -> TweetView(context)
                Provider.API_MASTODON -> DonStatusView(context)
                else -> throw NotImplementedError("API Type ${history.status.providerApiType} is not compatible.")
            }.also {
                it.userRecords = userRecords
                it.userExtras = userExtras
                it.mode = mode or Mode.INCLUDE
                flInclude.addView(it)
            }
        } else {
            flInclude.getChildAt(0) as StatusView
        }
        include.status = history.status
    }

    val NotifyHistory.kindString: String
        get() =
            when (kind) {
                NotifyHistory.KIND_FAVED -> "お気に入り登録"
                NotifyHistory.KIND_RETWEETED -> "ブースト"
                else -> "反応"
            }
}