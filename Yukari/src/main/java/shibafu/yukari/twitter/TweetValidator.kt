package shibafu.yukari.twitter

import com.twitter.Extractor
import com.twitter.Validator
import shibafu.yukari.util.PostValidator
import java.text.Normalizer

class TweetValidator : PostValidator {
    private val extractor = Extractor()

    override fun getMaxLength(): Int = 280

    override fun getMeasuredLength(text: String): Int {
        var normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)

        extractor.extractURLsWithIndices(normalizedText).reversed().forEach { entity ->
            normalizedText = normalizedText.replaceRange(entity.start, entity.end, urlPlaceHolder)
        }

        val codePointCount = normalizedText.codePointCount(0, normalizedText.length)
        var count = 0
        for (i in 0 until codePointCount) {
            val codePoint = normalizedText.codePointAt(i)
            count += codePointRanges.find { it.range.contains(codePoint) }?.weight ?: DEFAULT_WEIGHT
        }
        return count / SCALE
    }

    override fun isValidText(text: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private data class CodePointRange(val range: IntRange, val weight: Int)

    companion object {
        /** 文字数に換算するためのスケール */
        private const val SCALE = 100

        /** 1文字あたりの標準の重み */
        private const val DEFAULT_WEIGHT = 200

        /** URLとして取り扱う部分の長さ */
        private const val URL_LENGTH = 23

        /** 文字数計算用にURL部分に代わりに埋め込む文字列 */
        private val urlPlaceHolder = "_".repeat(URL_LENGTH)

        /**
         * コードポイント別の重み設定
         *
         * 参考: [https://developer.twitter.com/en/docs/developer-utilities/twitter-text]
         */
        private val codePointRanges = listOf(
                CodePointRange(range =    0..4351, weight = 100),
                CodePointRange(range = 8192..8205, weight = 100),
                CodePointRange(range = 8208..8223, weight = 100),
                CodePointRange(range = 8242..8247, weight = 100)
        )
    }
}

class TwitterTextValidator(isDirectMessage: Boolean) : PostValidator {
    private val validator = Validator()
    private val maxLength = if (isDirectMessage) 10000 else Validator.MAX_TWEET_LENGTH

    override fun getMaxLength(): Int = maxLength

    override fun getMeasuredLength(text: String): Int = validator.getTweetLength(text)

    override fun isValidText(text: String): Boolean = validator.isValidTweet(text)
}

object TweetValidatorFactory {
    @JvmStatic
    fun newInstance(isDirectMessage: Boolean): PostValidator =
            if (isDirectMessage) TwitterTextValidator(true)
            else TweetValidator()
}