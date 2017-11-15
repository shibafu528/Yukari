package shibafu.yukari.twitter

import com.twitter.Validator
import shibafu.yukari.util.PostValidator

private const val DM_MAX_LENGTH = 10000

class TweetValidator : PostValidator {
    override fun getMaxLength(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMeasuredLength(text: String): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isValidText(text: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class TwitterTextValidator(isDirectMessage: Boolean) : PostValidator {
    private val validator = Validator()
    private val maxLength = if (isDirectMessage) DM_MAX_LENGTH else Validator.MAX_TWEET_LENGTH

    override fun getMaxLength(): Int = maxLength

    override fun getMeasuredLength(text: String): Int = validator.getTweetLength(text)

    override fun isValidText(text: String): Boolean = validator.isValidTweet(text)
}

object TweetValidatorFactory {
    @JvmStatic
    fun newInstance(isDirectMessage: Boolean): PostValidator = TwitterTextValidator(isDirectMessage)
}