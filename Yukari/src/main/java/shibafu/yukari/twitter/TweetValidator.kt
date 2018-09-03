package shibafu.yukari.twitter

import com.twitter.Extractor
import com.twitter.Validator
import shibafu.yukari.linkage.PostValidator
import java.text.Normalizer

class TweetValidator : PostValidator {
    private val validator = Validator()
    private val extractor = Extractor()

    override fun getMaxLength(options: Map<String, Any?>): Int {
        if (options[OPTION_IS_DIRECT_MESSAGE] == true) {
            return 10000
        } else {
            return 280
        }
    }

    override fun getMeasuredLength(text: String, options: Map<String, Any?>): Int {
        if (options[OPTION_IS_DIRECT_MESSAGE] == true) {
            return validator.getTweetLength(text)
        }

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

        var subtracts = 0
        if (options[OPTION_INCLUDE_QUOTE_URL] == true) {
            subtracts = URL_LENGTH + 1
        }
        return count / SCALE - subtracts
    }

    private data class CodePointRange(val range: IntRange, val weight: Int)

    companion object {
        /** オプション : DirectMessageとして計算 (boolean) */
        const val OPTION_IS_DIRECT_MESSAGE = "IS_DIRECT_MESSAGE"

        /** オプション : 引用URL有りとして計算 (boolean) */
        const val OPTION_INCLUDE_QUOTE_URL = "INCLUDE_QUOTE_URL"

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
