package shibafu.yukari.fragment.status

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.PopupMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import shibafu.yukari.R
import shibafu.yukari.activity.MainActivity
import shibafu.yukari.common.StatusChildUI
import shibafu.yukari.common.StatusUI
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.fragment.base.TwitterFragment
import shibafu.yukari.service.AsyncCommandService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.defaultSharedPreferences

class StatusMainFragment2 : TwitterFragment(), StatusChildUI, SimpleAlertDialogFragment.OnDialogChoseListener {
    private val status: Status
        get() {
            val activity = this.activity
            if (activity is StatusUI) {
                return activity.status
            }
            throw IllegalStateException("親Activityに${StatusUI::class.java.simpleName}が実装されていないか、こいつが孤児.")
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

        ibFavorite.setOnClickListener {
            val status = status
            val userRecord = userRecord ?: return@setOnClickListener

            if (status.metadata.favoritedUsers.get(userRecord.NumericId)) {
                // お気に入り登録済
                destroyFavorite()
            } else {
                // お気に入り未登録
                val canRecursiveFavorite = status is TwitterStatus && !status.quoteEntities.isEmpty
                if (defaultSharedPreferences.getBoolean("pref_fav_with_quotes", false) && canRecursiveFavorite) {
                    val popupMenu = PopupMenu(context, ibFavorite)
                    popupMenu.menu.add(Menu.NONE, 0, Menu.NONE, "お気に入り登録")
                    popupMenu.menu.add(Menu.NONE, 1, Menu.NONE, "引用もまとめてお気に入り登録")
                    popupMenu.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            0 -> createFavorite()
                            1 -> createFavorite(withQuotes = true)
                        }
                        true
                    }
                    popupMenu.show()
                } else {
                    createFavorite()
                }
            }
        }

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

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_FAVORITE_NUISANCE -> {
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        createFavorite(withQuotes = extras?.getBoolean("withQuotes") ?: false, skipCheck = true)
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        val query = String.format("\"%s\" -RT", status.originStatus.text)
                        intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, query)
                        startActivity(intent)
                    }
                }
            }
            DIALOG_FAVORITE_CONFIRM -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    createFavorite(withQuotes = extras?.getBoolean("withQuotes") ?: false, skipCheck = true)
                }
            }
        }
    }

    override fun onUserChanged(userRecord: AuthUserRecord?) {}

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    private fun createFavorite(withQuotes: Boolean = false, skipCheck: Boolean = false) {
        val status = status
        val userRecord = userRecord ?: return

        if (!skipCheck) {
            if (defaultSharedPreferences.getBoolean("pref_guard_nuisance", true) && NUISANCES.contains(status.source)) {
                val dialog = SimpleAlertDialogFragment.Builder(DIALOG_FAVORITE_NUISANCE)
                        .setTitle("確認")
                        .setMessage("このツイートは${status.originStatus}を使用して投稿されています。お気に入り登録してもよろしいですか？")
                        .setPositive("ふぁぼる")
                        .setNeutral("本文で検索")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putBoolean("withQuotes", withQuotes) })
                        .build()
                dialog.setTargetFragment(this, DIALOG_FAVORITE_NUISANCE)
                dialog.show(childFragmentManager, "dialog_favorite_nuisance")
                return
            } else if (defaultSharedPreferences.getBoolean("pref_dialog_fav", false)) {
                val dialog = SimpleAlertDialogFragment.Builder(DIALOG_FAVORITE_CONFIRM)
                        .setTitle("確認")
                        .setMessage("お気に入り登録しますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putBoolean("withQuotes", withQuotes) })
                        .build()
                dialog.setTargetFragment(this, DIALOG_FAVORITE_CONFIRM)
                dialog.show(childFragmentManager, "dialog_favorite_confirm")
                return
            }
        }

        val intent = AsyncCommandService.createFavorite(activity, status, userRecord)
        activity.startService(intent)

        if (withQuotes && status is TwitterStatus) {
            status.quoteEntities.forEach { id ->
                val intent2 = AsyncCommandService.createFavorite(activity, id, userRecord)
                activity.startService(intent2)
            }
        }

        closeAfterFavorite()
    }

    private fun destroyFavorite() {
        val userRecord = userRecord ?: return

        val intent = AsyncCommandService.destroyFavorite(activity, status, userRecord)
        activity.startService(intent)

        closeAfterFavorite()
    }

    private fun closeAfterFavorite() {
        if (defaultSharedPreferences.getBoolean("pref_close_after_fav", false)) {
            activity.finish()
        }
    }

    companion object {
        private const val BUTTON_SHOW_DURATION = 260
        private val NUISANCES = arrayOf(
                "ShootingStar",
                "TheWorld",
                "Biyon",
                "MoonStrike",
                "NightFox"
        )

        private const val DIALOG_FAVORITE_NUISANCE = 1
        private const val DIALOG_FAVORITE_CONFIRM = 2
    }
}