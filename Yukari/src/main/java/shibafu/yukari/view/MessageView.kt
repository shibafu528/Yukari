package shibafu.yukari.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import shibafu.yukari.twitter.entity.TwitterMessage
import twitter4j.DirectMessage

/**
 * [DirectMessage]を表示するためのビュー
 */
class MessageView : StatusView {
    constructor(context: Context?, singleLine: Boolean) : super(context, singleLine)
    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val message = status as TwitterMessage

        tvTimestamp.text = String.format("%s to @%s / %s",
                tvTimestamp.text,
                message.recipient.screenName, message.recipient.name)
    }

    override fun updateIndicator() {
        super.updateIndicator()

        val message = status as TwitterMessage

        ivAccountColor.setBackgroundColor(Color.TRANSPARENT)
        for (authUserRecord in userRecords) {
            if (authUserRecord.NumericId == message.recipient.id) {
                ivAccountColor.setBackgroundColor(authUserRecord.AccountColor)
                break
            } else if (authUserRecord.NumericId == message.sender.id) {
                ivAccountColor.setBackgroundColor(authUserRecord.AccountColor)
            }
        }
    }
}