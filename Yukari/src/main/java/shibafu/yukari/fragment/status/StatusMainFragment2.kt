package shibafu.yukari.fragment.status

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import shibafu.yukari.R
import shibafu.yukari.common.StatusChildUI
import shibafu.yukari.common.StatusUI
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.base.TwitterFragment
import shibafu.yukari.twitter.AuthUserRecord


class StatusMainFragment2 : TwitterFragment(), StatusChildUI {
    private val status: Status?
        get() {
            val activity = this.activity
            if (activity is StatusUI) {
                return activity.status
            }
            return null
        }

    private var userRecord: AuthUserRecord?
        get() {
            val activity = this.activity
            if (activity is StatusUI) {
                return activity.userRecord
            }
            return null
        }
        set(value) {
            val activity = this.activity
            if (activity is StatusUI) {
                activity.userRecord = value
            }
        }

    private lateinit var ibReply: ImageButton
    private lateinit var ibRetweet: ImageButton
    private lateinit var ibFavorite: ImageButton
    private lateinit var ibFavRt: ImageButton
    private lateinit var ibQuote: ImageButton
    private lateinit var ibShare: ImageButton
    private lateinit var ibAccount: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_status_main, container, false)
        ibReply = v.findViewById(R.id.ib_state_reply) as ImageButton
        ibRetweet = v.findViewById(R.id.ib_state_retweet) as ImageButton
        ibFavorite = v.findViewById(R.id.ib_state_favorite) as ImageButton
        ibFavRt = v.findViewById(R.id.ib_state_favrt) as ImageButton
        ibQuote = v.findViewById(R.id.ib_state_quote) as ImageButton
        ibShare = v.findViewById(R.id.ib_state_share) as ImageButton
        ibAccount = v.findViewById(R.id.ib_state_account) as ImageButton
        return v
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val delta = resources.getDimensionPixelSize(R.dimen.status_button_delta).toFloat()

        ObjectAnimator.ofPropertyValuesHolder(ibReply,
                PropertyValuesHolder.ofFloat("translationX", 0f, -(delta / 2)),
                PropertyValuesHolder.ofFloat("translationY", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()
        ObjectAnimator.ofPropertyValuesHolder(ibRetweet,
                PropertyValuesHolder.ofFloat("translationX", 0f, delta / 2),
                PropertyValuesHolder.ofFloat("translationY", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()
        ObjectAnimator.ofPropertyValuesHolder(ibFavorite,
                PropertyValuesHolder.ofFloat("translationX", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()
        ObjectAnimator.ofPropertyValuesHolder(ibQuote,
                PropertyValuesHolder.ofFloat("translationX", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()
        ObjectAnimator.ofPropertyValuesHolder(ibFavRt,
                PropertyValuesHolder.ofFloat("translationX", 0f, -(delta / 2)),
                PropertyValuesHolder.ofFloat("translationY", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()
        ObjectAnimator.ofPropertyValuesHolder(ibShare,
                PropertyValuesHolder.ofFloat("translationX", 0f, delta / 2),
                PropertyValuesHolder.ofFloat("translationY", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION.toLong())
                .start()

        userRecord?.let {
            ImageLoaderTask.loadProfileIcon(activity, ibAccount, it.ProfileImageUrl)
        }
    }

    override fun onUserChanged(userRecord: AuthUserRecord?) {}

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    companion object {
        private const val BUTTON_SHOW_DURATION = 260
    }
}