package shibafu.yukari.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.databinding.ViewTimelineErrorBinding
import shibafu.yukari.linkage.RestQueryException
import twitter4j.TwitterException

class TimelineErrorView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding = ViewTimelineErrorBinding.inflate(LayoutInflater.from(context), this, true)
    private var onCloseListener: OnCloseListener? = null

    var title: CharSequence
        get() = binding.tvErrorTitle.text
        set(value) {
            binding.tvErrorTitle.text = value
        }

    var message: CharSequence
        get() = binding.tvErrorMessage.text
        set(value) {
            binding.tvErrorMessage.text = value
        }

    init {
        binding.ibCloseError.setOnClickListener {
            onCloseListener?.onCloseTimelineError(this)
        }
    }

    fun setOnCloseListener(listener: OnCloseListener) {
        onCloseListener = listener
    }

    fun setException(exception: RestQueryException) {
        val cause = exception.cause ?: return
        when (cause) {
            is TwitterException -> {
                when (cause.statusCode) {
                    429 -> {
                        title = "429:${cause.errorCode} レートリミット超過 | @${exception.userRecord.ScreenName}"
                        message = String.format("次回リセット: %d分%d秒後\n時間を空けて再度操作してください",
                                cause.rateLimitStatus.secondsUntilReset / 60,
                                cause.rateLimitStatus.secondsUntilReset % 60)
                    }
                    403 -> {
                        title = "403:${cause.errorCode} アクセス権エラー | @${exception.userRecord.ScreenName}"
                        message = when (cause.errorCode) {
                            93 -> "DMへのアクセスが制限されています。\n一度アプリ連携を切って認証を再発行してみてください。"
                            else -> cause.errorMessage
                        }
                    }
                    else -> {
                        val kind = if (cause.isCausedByNetworkIssue) { "通信エラー" } else { "エラー" }
                        title = "${cause.statusCode}:${cause.errorCode} $kind | @${exception.userRecord.ScreenName}"
                        message = cause.errorMessage ?: "TLの取得中にエラーが発生しました\n${(cause.cause ?: cause)}"
                    }
                }
            }
            is Mastodon4jRequestException -> {
                val response = cause.response
                if (response != null) {
                    title = "${response.code()} エラー | @${exception.userRecord.ScreenName}"
                    message = response.message()
                } else {
                    title = "エラー | @${exception.userRecord.ScreenName}"
                    message = "TLの取得中にエラーが発生しました\n${(cause.cause ?: cause)}"
                }
            }
            else -> {
                title = "エラー | @${exception.userRecord.ScreenName}"
                message = "TLの取得中にエラーが発生しました\n${cause}"
            }
        }
    }

    interface OnCloseListener {
        fun onCloseTimelineError(v: TimelineErrorView)
    }
}