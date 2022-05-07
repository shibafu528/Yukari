package shibafu.yukari.view

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.cardview.widget.CardView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import shibafu.yukari.R

class ProfileButton : CardView {

    private val ivIcon: ImageView
    private val tvCount: TextView
    private val tvText: TextView

    var icon: Drawable
        get() = ivIcon.drawable
        set(value) {
            ivIcon.setImageDrawable(value)
        }

    var count: CharSequence
        get() = tvCount.text
        set(value) {
            tvCount.text = value
        }

    var text: CharSequence
        get() = tvText.text
        set(value) {
            tvText.text = value
        }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.cardViewStyle) : super(context, attrs, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.view_profile_button, this, true)

        ivIcon = findViewById(R.id.ivProfileButtonIcon)
        tvCount = findViewById(R.id.tvProfileButtonCount)
        tvText = findViewById(R.id.tvProfileButtonText)

        val attr = context.obtainStyledAttributes(attrs, R.styleable.ProfileButton, defStyleAttr, 0)

        attr.getDrawable(R.styleable.ProfileButton_iconSrc)?.let {
            ivIcon.setImageDrawable(it)
        }

        attr.getString(R.styleable.ProfileButton_count)?.let {
            tvCount.text = it
        }

        attr.getString(R.styleable.ProfileButton_text)?.let {
            tvText.text = it
        }

        attr.recycle()
    }
}