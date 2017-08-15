package shibafu.yukari.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import shibafu.yukari.twitter.TweetCommon
import shibafu.yukari.twitter.TweetCommonDelegate
import twitter4j.DirectMessage

class MessageView : StatusView {
    // Delegate
    override val delegate: TweetCommonDelegate = TweetCommon.newInstance(DirectMessage::class.java)

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun updateTimestamp(typeface: Typeface, fontSize: Float) {
        super.updateTimestamp(typeface, fontSize)

        val message = status as DirectMessage

        tvTimestamp.text = String.format("%s to @%s / %s",
                tvTimestamp.text,
                message.recipientScreenName, message.recipient.name)
    }

    override fun updateIndicator() {
        super.updateIndicator()

        val message = status as DirectMessage

        ivAccountColor.setBackgroundColor(Color.TRANSPARENT)
        for (authUserRecord in userRecords) {
            if (authUserRecord.NumericId == message.recipientId) {
                ivAccountColor.setBackgroundColor(authUserRecord.AccountColor)
                break
            } else if (authUserRecord.NumericId == message.senderId) {
                ivAccountColor.setBackgroundColor(authUserRecord.AccountColor)
            }
        }
    }
}