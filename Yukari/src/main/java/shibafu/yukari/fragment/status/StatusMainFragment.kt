package shibafu.yukari.fragment.status

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.PopupMenu
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import shibafu.yukari.R
import shibafu.yukari.activity.AccountChooserActivity
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.common.StatusChildUI
import shibafu.yukari.common.StatusUI
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.fragment.SimpleListDialogFragment
import shibafu.yukari.fragment.base.YukariBaseFragment
import shibafu.yukari.service.AsyncCommandService
import shibafu.yukari.service.PostService
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.defaultSharedPreferences
import java.util.*

class StatusMainFragment : YukariBaseFragment(), StatusChildUI, SimpleAlertDialogFragment.OnDialogChoseListener, SimpleListDialogFragment.OnDialogChoseListener {
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

    private var limitedQuote: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_status_main, container, false)

        ibReply = v.findViewById(R.id.ib_state_reply) as ImageButton
        ibRetweet = v.findViewById(R.id.ib_state_retweet) as ImageButton
        ibFavorite = v.findViewById(R.id.ib_state_favorite) as ImageButton
        ibFavRt = v.findViewById(R.id.ib_state_favrt) as ImageButton
        ibQuote = v.findViewById(R.id.ib_state_quote) as ImageButton
        ibShare = v.findViewById(R.id.ib_state_share) as ImageButton
        ibAccount = v.findViewById(R.id.ib_state_account) as ImageButton

        ibReply.setOnClickListener {
            val status = status
            val userRecord = userRecord ?: return@setOnClickListener

            if (!(status.getStatusRelation(listOf(userRecord)) == Status.RELATION_MENTIONED_TO_ME && status.mentions.size == 1) &&
                    status.mentions.isNotEmpty() && defaultSharedPreferences.getBoolean("pref_choose_reply_to", true)) {
                val popupMenu = PopupMenu(requireContext(), ibReply)
                popupMenu.inflate(R.menu.reply_to)
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_reply_to_sender -> {
                            replyToSender()
                            return@setOnMenuItemClickListener true
                        }
                        R.id.action_reply_to_all_mentions -> {
                            replyToAllMentions()
                            return@setOnMenuItemClickListener true
                        }
                    }
                    false
                }
                popupMenu.show()
            } else {
                replyToSender()
            }
        }
        ibReply.setOnLongClickListener {
            replyToAllMentions()
            true
        }

        ibFavorite.setOnClickListener {
            val status = status
            val userRecord = userRecord ?: return@setOnClickListener

            if (status.metadata.favoritedUsers.contains(userRecord.InternalId)) {
                // お気に入り登録済
                destroyFavorite()
            } else {
                // お気に入り未登録
                val canRecursiveFavorite = status is TwitterStatus && !status.quoteEntities.isEmpty()
                if (defaultSharedPreferences.getBoolean("pref_fav_with_quotes", false) && canRecursiveFavorite) {
                    val popupMenu = PopupMenu(requireContext(), ibFavorite)
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
        ibFavorite.setOnLongClickListener {
            if (status.providerApiType == Provider.API_TWITTER) {
                Toast.makeText(activity, "TwitterではマルチアカウントFavを使用できません。", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            val intent = Intent(activity, AccountChooserActivity::class.java)
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true)
            intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, status.providerApiType)
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav")
            startActivityForResult(intent, REQUEST_MULTI_FAVORITE)
            Toast.makeText(activity,
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show()
            true
        }

        ibRetweet.setOnClickListener {
            if (defaultSharedPreferences.getBoolean("pref_dialog_rt", true)) {
                val message = if (status.getStatusRelation(twitterService.users) == Status.RELATION_OWNED && defaultSharedPreferences.getBoolean("pref_too_late_delete_message", false)) {
                    "過去の栄光にすがりますか？"
                } else {
                    "ブーストしますか？"
                }
                val dialog = SimpleAlertDialogFragment.Builder(DIALOG_REPOST_CONFIRM)
                        .setTitle("確認")
                        .setMessage(message)
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .build()
                dialog.setTargetFragment(this, DIALOG_REPOST_CONFIRM)
                dialog.show(parentFragmentManager, "dialog_repost_confirm")
            } else {
                val userRecord = userRecord ?: return@setOnClickListener

                val activity = requireActivity()
                val intent = AsyncCommandService.createRepost(activity, status, userRecord)
                activity.startService(intent)

                closeAfterFavorite()
            }
        }
        ibRetweet.setOnLongClickListener {
            if (status.providerApiType == Provider.API_TWITTER) {
                Toast.makeText(activity, "TwitterではマルチアカウントRTを使用できません。", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            val intent = Intent(activity, AccountChooserActivity::class.java)
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true)
            intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, status.providerApiType)
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントRT")
            startActivityForResult(intent, REQUEST_MULTI_REPOST)
            Toast.makeText(activity,
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show()
            true
        }

        ibFavRt.setOnClickListener {
            if (defaultSharedPreferences.getBoolean("pref_dialog_favrt", true)) {
                val dialog = SimpleAlertDialogFragment.Builder(DIALOG_FAV_AND_REPOST_CONFIRM)
                        .setTitle("確認")
                        .setMessage("お気に入りに登録してBTしますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .build()
                dialog.setTargetFragment(this, DIALOG_FAV_AND_REPOST_CONFIRM)
                dialog.show(parentFragmentManager, "dialog_fav_and_repost_confirm")
            } else {
                val userRecord = userRecord ?: return@setOnClickListener

                val activity = requireActivity()
                val intent = AsyncCommandService.createFavAndRepost(activity, status, userRecord)
                activity.startService(intent)

                closeAfterFavorite()
            }
        }
        ibFavRt.setOnLongClickListener {
            if (status.providerApiType == Provider.API_TWITTER) {
                Toast.makeText(activity, "TwitterではマルチアカウントFav&RTを使用できません。", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            val intent = Intent(activity, AccountChooserActivity::class.java)
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true)
            intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, status.providerApiType)
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav&RT")
            startActivityForResult(intent, REQUEST_MULTI_FAVRT)
            Toast.makeText(activity,
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show()
            true
        }

        ibQuote.setOnClickListener {
            if (limitedQuote) {
                openLimitedQuote()
                return@setOnClickListener
            }

            val defaultQuote = defaultSharedPreferences.getString("pref_default_quote_2_0_1", "-1")!!.toInt()
            if (defaultQuote < 0) {
                openQuoteStyleSelector()
                return@setOnClickListener
            }

            if (quoteStatus(defaultQuote)) {
                val quoteStyles = resources.getStringArray(R.array.pref_quote_entries).let { it.copyOfRange(1, it.size) }

                val toast = Toast.makeText(activity, quoteStyles[defaultQuote], Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.TOP or Gravity.CENTER, 0, 0)
                toast.show()
            }
        }
        ibQuote.setOnLongClickListener {
            if (limitedQuote) {
                openLimitedQuote()
                return@setOnLongClickListener true
            }

            openQuoteStyleSelector()
            true
        }

        ibShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (limitedQuote) {
                intent.putExtra(Intent.EXTRA_TEXT, status.url)
            } else {
                intent.putExtra(Intent.EXTRA_TEXT, status.toSTOTFormat())
            }
            startActivity(intent)
        }

        ibAccount.setOnClickListener {
            // TODO: 変更後に操作互換性判定をしてあげて、最低限URL引用くらいはできるとかはアリだと思う
            val intent = Intent(activity, AccountChooserActivity::class.java)
            intent.putExtra(Intent.EXTRA_TITLE, "アカウント切り替え")
            intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, status.providerApiType)
            startActivityForResult(intent, REQUEST_CHANGE_ACCOUNT)
        }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

        onUserChanged(userRecord)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CHANGE_ACCOUNT -> {
                    userRecord = data?.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD) as? AuthUserRecord
                            ?: return
                }
                REQUEST_REPLY, REQUEST_QUOTE -> {
                    requireActivity().finish()
                }
                REQUEST_RT_QUOTE -> {
                    val draft = data?.getParcelableExtra(TweetActivity.EXTRA_DRAFT) as? StatusDraft ?: return
                    val activity = requireActivity()
                    //これ、RT失敗してもツイートしちゃうんですよねえ
                    ContextCompat.startForegroundService(activity,
                            PostService.newIntent(activity, draft,
                                    PostService.FLAG_RETWEET,
                                    status))
                    activity.finish()
                }
                REQUEST_FRT_QUOTE -> {
                    val draft = data?.getParcelableExtra(TweetActivity.EXTRA_DRAFT) as? StatusDraft ?: return
                    val activity = requireActivity()
                    //これ、RT失敗してもツイートしちゃうんですよねえ
                    ContextCompat.startForegroundService(activity,
                            PostService.newIntent(activity, draft,
                                    PostService.FLAG_RETWEET or PostService.FLAG_FAVORITE,
                                    status))
                    activity.finish()
                }
                REQUEST_MULTI_FAVORITE -> {
                    if (data == null) {
                        return
                    }
                    val activity = requireActivity()
                    val selectedUsers = data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS) as ArrayList<AuthUserRecord>
                    selectedUsers.map { AsyncCommandService.createFavorite(activity.applicationContext, status, it) }
                            .forEach { activity.startService(it) }
                }
                REQUEST_MULTI_REPOST -> {
                    if (data == null) {
                        return
                    }
                    val activity = requireActivity()
                    val selectedUsers = data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS) as ArrayList<AuthUserRecord>
                    selectedUsers.map { AsyncCommandService.createRepost(activity.applicationContext, status, it) }
                            .forEach { activity.startService(it) }
                }
                REQUEST_MULTI_FAVRT -> {
                    if (data == null) {
                        return
                    }
                    val activity = requireActivity()
                    val selectedUsers = data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS) as ArrayList<AuthUserRecord>
                    selectedUsers.map { AsyncCommandService.createFavAndRepost(activity.applicationContext, status, it) }
                            .forEach { activity.startService(it) }
                }
            }
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_FAVORITE_CONFIRM -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    createFavorite(withQuotes = extras?.getBoolean("withQuotes") ?: false, skipCheck = true)
                }
            }
            DIALOG_REPOST_CONFIRM -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val userRecord = userRecord ?: return

                    val activity = requireActivity()
                    val intent = AsyncCommandService.createRepost(activity, status, userRecord)
                    activity.startService(intent)

                    closeAfterFavorite()
                }
            }
            DIALOG_FAV_AND_REPOST_CONFIRM -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val userRecord = userRecord ?: return

                    val activity = requireActivity()
                    val intent = AsyncCommandService.createFavAndRepost(activity, status, userRecord)
                    activity.startService(intent)

                    closeAfterFavorite()
                }
            }
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, value: String?, extras: Bundle?) {
        when (requestCode) {
            DIALOG_QUOTE_SELECT -> {
                if (which != DialogInterface.BUTTON_NEGATIVE) {
                    quoteStatus(which)
                }
            }
        }
    }

    override fun onUserChanged(userRecord: AuthUserRecord?) {
        if (userRecord != null) {
            ImageLoaderTask.loadProfileIcon(activity, ibAccount, userRecord.ProfileImageUrl)

            ibRetweet.isEnabled = status.canRepost(userRecord)
            ibFavorite.isEnabled = status.canFavorite(userRecord)

            if (isTwitterServiceBound) {
                // 自分の所有ステータスの場合、ナルシストオプションが有効になってないなら強制ふぁぼ禁止にする
                // ...セルフふぁぼができないと思いこんでいたという経緯が残ってないと意味わからんな
                if (status.getStatusRelation(twitterService.users) == Status.RELATION_OWNED && !defaultSharedPreferences.getBoolean("pref_narcist", false)) {
                    ibFavorite.isEnabled = false
                }
            }

            ibFavRt.isEnabled = ibRetweet.isEnabled && ibFavorite.isEnabled

            // RT可能なステータスのみ引用可能として扱う
            limitedQuote = !status.canRepost(userRecord)
        }
    }

    override fun onServiceConnected() {
        onUserChanged(userRecord)
    }

    override fun onServiceDisconnected() {}

    private fun replyToSender() {
        val userRecord = userRecord

        val intent = Intent(activity, TweetActivity::class.java)
        intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
        intent.putExtra(TweetActivity.EXTRA_STATUS, status.originStatus)
        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
        if (userRecord != null && status.originStatus.user.screenName != userRecord.ScreenName) {
            intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + status.originStatus.user.screenName + " ")
        }
        startActivityForResult(intent, REQUEST_REPLY)
    }

    private fun replyToAllMentions() {
        val userRecord = userRecord ?: return

        val intent = Intent(activity, TweetActivity::class.java)
        intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
        intent.putExtra(TweetActivity.EXTRA_STATUS, status.originStatus)
        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
        intent.putExtra(TweetActivity.EXTRA_TEXT, StringBuilder().apply {
            if (status.originStatus.user.screenName != userRecord.ScreenName) {
                append("@").append(status.originStatus.user.screenName).append(" ")
            }
            status.mentions.forEach { mention ->
                if (!this.toString().contains("@" + mention.screenName) && mention.screenName != userRecord.ScreenName) {
                    append("@").append(mention.screenName).append(" ")
                }
            }
        }.toString())
        startActivityForResult(intent, REQUEST_REPLY)
    }

    private fun createFavorite(withQuotes: Boolean = false, skipCheck: Boolean = false) {
        val status = status
        val userRecord = userRecord ?: return

        if (!skipCheck) {
            if (defaultSharedPreferences.getBoolean("pref_dialog_fav", false)) {
                val dialog = SimpleAlertDialogFragment.Builder(DIALOG_FAVORITE_CONFIRM)
                        .setTitle("確認")
                        .setMessage("お気に入り登録しますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putBoolean("withQuotes", withQuotes) })
                        .build()
                dialog.setTargetFragment(this, DIALOG_FAVORITE_CONFIRM)
                dialog.show(parentFragmentManager, "dialog_favorite_confirm")
                return
            }
        }

        val activity = requireActivity()
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

        val activity = requireActivity()
        val intent = AsyncCommandService.destroyFavorite(activity, status, userRecord)
        activity.startService(intent)

        closeAfterFavorite()
    }

    private fun closeAfterFavorite() {
        if (defaultSharedPreferences.getBoolean("pref_close_after_fav", false)) {
            requireActivity().finish()
        }
    }

    private fun openLimitedQuote() {
        val intent = Intent(activity, TweetActivity::class.java)
        intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
        intent.putExtra(TweetActivity.EXTRA_STATUS, status)
        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE)
        intent.putExtra(TweetActivity.EXTRA_TEXT, " " + status.url)
        startActivityForResult(intent, REQUEST_QUOTE)
    }

    private fun openQuoteStyleSelector() {
        val quoteStyles = resources.getStringArray(R.array.pref_quote_entries).let { it.copyOfRange(1, it.size) }

        val dialog = SimpleListDialogFragment.newInstance(DIALOG_QUOTE_SELECT,
                "引用形式を選択", null,
                null, "キャンセル",
                *quoteStyles)
        dialog.setTargetFragment(this, DIALOG_QUOTE_SELECT)
        dialog.show(parentFragmentManager, "dialog_quote_select")
    }

    private fun quoteStatus(style: Int): Boolean {
        val intent = Intent(activity, TweetActivity::class.java)
        intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
        intent.putExtra(TweetActivity.EXTRA_STATUS, status)
        if (style < 3) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE)
            when (style) {
                0 -> intent.putExtra(TweetActivity.EXTRA_TEXT, " RT @${status.originStatus.user.screenName}: ${status.originStatus.text}")
                1 -> intent.putExtra(TweetActivity.EXTRA_TEXT, " QT @${status.originStatus.user.screenName}: ${status.originStatus.text}")
                2 -> intent.putExtra(TweetActivity.EXTRA_TEXT, " " + status.originStatus.url)
            }
            startActivityForResult(intent, REQUEST_QUOTE)
        } else {
            var request = -1
            when (style) {
                3 -> {
                    if (!ibRetweet.isEnabled) {
                        Toast.makeText(activity, "BTできない投稿です。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    request = REQUEST_RT_QUOTE
                }
                4 -> {
                    if (!ibFavRt.isEnabled) {
                        Toast.makeText(activity, "FavBTできない投稿です。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    request = REQUEST_FRT_QUOTE
                }
            }
            if (request > -1) {
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_COMPOSE)
                intent.putExtra(TweetActivity.EXTRA_TEXT, defaultSharedPreferences.getString("pref_quote_comment_footer", " ＞BT"))
                startActivityForResult(intent, request)
            }
        }
        return true
    }

    companion object {
        private const val BUTTON_SHOW_DURATION = 260

        private const val REQUEST_CHANGE_ACCOUNT = 0
        private const val REQUEST_REPLY = 1
        private const val REQUEST_QUOTE = 2
        private const val REQUEST_RT_QUOTE = 3
        private const val REQUEST_FRT_QUOTE = 4
        private const val REQUEST_MULTI_FAVORITE = 5
        private const val REQUEST_MULTI_REPOST = 6
        private const val REQUEST_MULTI_FAVRT = 7

        private const val DIALOG_FAVORITE_CONFIRM = 1
        private const val DIALOG_REPOST_CONFIRM = 2
        private const val DIALOG_FAV_AND_REPOST_CONFIRM = 3
        private const val DIALOG_QUOTE_SELECT = 4
    }
}