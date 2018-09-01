package shibafu.yukari.mastodon

import com.twitter.Validator
import shibafu.yukari.util.PostValidator

/**
 * Mastodon用の文字数計算・バリデーション実装
 * @param maxLength インスタンスにおける投稿可能な最大文字数。Mastodonの既定値は 500 だが、カスタマイズされている場合もある。
 */
class MastodonValidator(private val maxLength: Int = 500) : PostValidator {
    private val validator = Validator()

    override fun getMaxLength(): Int = maxLength

    override fun getMeasuredLength(text: String): Int = validator.getTweetLength(text)

    override fun isValidText(text: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}