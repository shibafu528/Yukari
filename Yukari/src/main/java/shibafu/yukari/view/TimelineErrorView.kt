package shibafu.yukari.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import shibafu.yukari.databinding.ViewTimelineErrorBinding

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

    interface OnCloseListener {
        fun onCloseTimelineError(v: TimelineErrorView)
    }
}