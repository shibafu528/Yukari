package shibafu.yukari.fragment.status

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import shibafu.yukari.R
import shibafu.yukari.activity.MastodonReportActivity
import shibafu.yukari.activity.MuteActivity
import shibafu.yukari.common.StatusChildUI
import shibafu.yukari.common.StatusUI
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.MuteMatch
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.ListRegisterDialogFragment
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.fragment.base.ListYukariBaseFragment
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.PluginApplicable
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.showToast
import java.util.*
import java.util.regex.Pattern

/**
 * Created by shibafu on 2016/02/05.
 */
class StatusActionFragment : ListYukariBaseFragment(), AdapterView.OnItemClickListener, SimpleAlertDialogFragment.OnDialogChoseListener, StatusChildUI {
    companion object {
        private const val REQUEST_DELETE = 0
    }

    private var itemList: List<StatusAction> = emptyList()
    private var isLoadedPluggaloid = false

    private val itemTemplates: List<Pair<StatusAction, () -> Boolean>> = listOf(
            Action("ブラウザで開く") {
                startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_VIEW, Uri.parse(status.originStatus.url))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        null))
            } visibleWhen { !status.originStatus.url.isNullOrEmpty() },

            Action("本文をコピー") {
                val cm = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("", status.originStatus.text))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    showToast("本文をコピーしました")
                }
            } visibleWhen { status.originStatus.text.isNotEmpty() },

            Action("パーマリンクをコピー") {
                val cm = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("", status.originStatus.url))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    showToast("リンクをコピーしました")
                }
            } visibleWhen { !status.originStatus.url.isNullOrEmpty() },

            Action("ブックマークに追加") {
                twitterService.database.updateRecord(Bookmark(status as TwitterStatus))
                showToast("ブックマークしました")
            } visibleWhen { status is TwitterStatus },

            Action("リストへ追加/削除") {
                ListRegisterDialogFragment.newInstance((status.originStatus.user as TwitterUser).user).let {
                    it.setTargetFragment(this, 0)
                    it.show(parentFragmentManager, "register")
                }
            } visibleWhen { status is TwitterStatus },

            Action("ミュートする") {
                MuteMenuDialogFragment.newInstance(status, this).show(parentFragmentManager, "mute")
            } visibleWhen { true },

            Action("投稿を削除") {
                val message = if (defaultSharedPreferences.getBoolean("pref_too_late_delete_message", false)) {
                    "失った信頼はもう戻ってきませんが、本当にこのつぶやきを削除しますか？"
                } else {
                    "投稿を削除しますか？"
                }
                val dialog = SimpleAlertDialogFragment.newInstance(REQUEST_DELETE, "確認", message, "OK", "キャンセル")
                dialog.setTargetFragment(this, REQUEST_DELETE)
                dialog.show(parentFragmentManager, "delete")
            } visibleWhen {
                val status = status
                status.user.identicalUrl == userRecord?.IdenticalUrl || status is Bookmark
            },

            Action("通報する") {
                val userRecord = userRecord ?: return@Action
                startActivity(MastodonReportActivity.newIntent(requireContext(), userRecord, status as DonStatus))
            } visibleWhen { status is DonStatus }
    )

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (defaultSharedPreferences.getString("pref_theme", "light")!!.endsWith("dark")) {
            view.setBackgroundResource(R.drawable.dialog_full_material_dark)
        } else {
            view.setBackgroundResource(R.drawable.dialog_full_material_light)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val status = status.originStatus
        val plugins: List<TwiccaPluginAction> =
                if (status is PluginApplicable && status.isApplicablePlugin) {
                    val query = Intent("jp.r246.twicca.ACTION_SHOW_TWEET").addCategory(Intent.CATEGORY_DEFAULT)

                    requireActivity().packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
                            .sortedWith(ResolveInfo.DisplayNameComparator(requireActivity().packageManager))
                            .map { TwiccaPluginAction(it) }
                } else {
                    emptyList()
                }

        itemList = itemTemplates.filter { it.second() }.map { it.first } + plugins

        listAdapter = ArrayAdapter<StatusAction>(requireActivity(), android.R.layout.simple_list_item_1, itemList)
        listView.onItemClickListener = this
        listView.isStackFromBottom = defaultSharedPreferences.getBoolean("pref_bottom_stack", false)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        childFragmentManager.fragments.forEach {
            it.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        itemList[position].onClick()
    }

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            REQUEST_DELETE -> if (which == DialogInterface.BUTTON_POSITIVE) {
                ParallelAsyncTask.executeParallel {
                    val status = status
                    val userRecord = userRecord ?: return@executeParallel

                    if (status is Bookmark) {
                        twitterService.database.deleteRecord(status)
                    } else {
                        twitterService.getProviderApi(userRecord)?.destroyStatus(userRecord, this.status)
                    }
                }
                requireActivity().finish()
            }
        }
    }

    private fun onSelectedMuteOption(muteOption: QuickMuteOption) {
        startActivity(muteOption.toIntent(requireActivity()))
    }

    override fun onUserChanged(userRecord: AuthUserRecord?) {}

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    /**
     * リストアップするコマンドのインターフェース
     */
    private abstract class StatusAction {
        /** 表示名 */
        abstract val label: String
        /** クリック時の処理 */
        abstract fun onClick()

        override fun toString(): String = label
    }

    /**
     * コマンドの簡易宣言用クラス
     */
    private class Action(override val label: String, val onClick: () -> Unit) : StatusAction() {
        override fun onClick() = onClick.invoke()

        /**
         * いつ表示するかの条件判定式との[Pair]を作成します。
         */
        infix fun visibleWhen(condition: () -> Boolean): Pair<StatusAction, () -> Boolean> = this to condition
    }

    /**
     * Twicca Pluginとの相互運用コマンドクラス
     */
    private inner class TwiccaPluginAction(private val resolveInfo: ResolveInfo) : StatusAction() {
        override val label: String = resolveInfo.activityInfo.loadLabel(requireActivity().packageManager).toString()

        override fun onClick() {
            val status = this@StatusActionFragment.status.originStatus

            val intent = Intent("jp.r246.twicca.ACTION_SHOW_TWEET")
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(resolveInfo.activityInfo.packageName)
                .setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                .putExtra(Intent.EXTRA_TEXT, status.text)
                .putExtra("id", status.id.toString())
                .putExtra("created_at", status.createdAt.time.toString())
                .putExtra("user_screen_name", status.user.screenName)
                .putExtra("user_name", status.user.name)
                .putExtra("user_id", status.user.id.toString())
                .putExtra("user_profile_image_url", status.user.profileImageUrl)
                .putExtra("user_profile_image_url_mini", status.user.profileImageUrl)
                .putExtra("user_profile_image_url_normal", status.user.profileImageUrl)
                .putExtra("user_profile_image_url_bigger", status.user.biggerProfileImageUrl)

            if (status.inReplyToId > -1) {
                intent.putExtra("in_reply_to_status_id", status.inReplyToId)
            }

            val matcher = Pattern.compile("<a .*>(.+)</a>").matcher(status.source)
            val via: String =
                    if (matcher.find()) matcher.group(1)
                    else                status.source
            intent.putExtra("source", via)

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                showToast("プラグインの起動に失敗しました\nアプリが削除されましたか？")
            }
        }
    }

    class MuteMenuDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val arguments = arguments ?: throw IllegalStateException("arguments is null!")
            val muteOptions = QuickMuteOption.fromStatus(arguments.getSerializable("status") as Status)
            val items = muteOptions.map { it.toString() }.toTypedArray()

            val dialog = AlertDialog.Builder(activity)
                    .setTitle("ミュート")
                    .setItems(items) { dialog1, which ->
                        dismiss()
                        (targetFragment as StatusActionFragment).onSelectedMuteOption(muteOptions[which])
                    }.setNegativeButton("キャンセル") { dialog1, which -> }.create()
            return dialog
        }

        companion object {

            fun newInstance(status: Status, target: StatusActionFragment): MuteMenuDialogFragment {
                val fragment = MuteMenuDialogFragment()
                val args = Bundle()
                args.putSerializable("status", status)
                fragment.arguments = args
                fragment.setTargetFragment(target, 0)
                return fragment
            }
        }
    }

    private class QuickMuteOption private constructor(private val type: Int, private val value: String) {

        override fun toString(): String {
            return when (type) {
                TYPE_TEXT -> "本文"
                TYPE_USER_NAME -> "ユーザー名($value)"
                TYPE_SCREEN_NAME -> "スクリーンネーム(@$value)"
                TYPE_USER_ID -> "ユーザーID($value)"
                TYPE_VIA -> "クライアント名($value)"
                TYPE_HASHTAG -> "#" + value
                TYPE_MENTION -> "@" + value
                else -> value
            }
        }

        fun toIntent(context: Context): Intent {
            var query = value
            var which = MuteConfig.SCOPE_TEXT
            var match = MuteMatch.MATCH_EXACT
            when (type) {
                TYPE_TEXT -> which = MuteConfig.SCOPE_TEXT
                TYPE_USER_NAME -> which = MuteConfig.SCOPE_USER_NAME
                TYPE_SCREEN_NAME -> which = MuteConfig.SCOPE_USER_SN
                TYPE_USER_ID -> which = MuteConfig.SCOPE_USER_ID
                TYPE_VIA -> which = MuteConfig.SCOPE_VIA
                TYPE_HASHTAG -> {
                    query = "[#＃]" + value
                    match = MuteMatch.MATCH_REGEX
                }
                TYPE_MENTION -> {
                    query = "@" + value
                    match = MuteMatch.MATCH_PARTIAL
                }
                TYPE_URL -> match = MuteMatch.MATCH_PARTIAL
            }
            return Intent(context, MuteActivity::class.java)
                    .putExtra(MuteActivity.EXTRA_QUERY, query)
                    .putExtra(MuteActivity.EXTRA_SCOPE, which)
                    .putExtra(MuteActivity.EXTRA_MATCH, match)
        }

        companion object {
            const val TYPE_TEXT = 0
            const val TYPE_USER_NAME = 1
            const val TYPE_SCREEN_NAME = 2
            const val TYPE_USER_ID = 3
            const val TYPE_VIA = 4
            const val TYPE_HASHTAG = 5
            const val TYPE_MENTION = 6
            const val TYPE_URL = 7

            fun fromStatus(status: Status): Array<QuickMuteOption> {
                val options = ArrayList<QuickMuteOption>()
                options.add(QuickMuteOption(TYPE_TEXT, status.text))
                options.add(QuickMuteOption(TYPE_USER_NAME, status.originStatus.user.name))
                options.add(QuickMuteOption(TYPE_SCREEN_NAME, status.originStatus.user.screenName))
                options.add(QuickMuteOption(TYPE_USER_ID, status.originStatus.user.id.toString()))
                if (status.source.isNotEmpty()) {
                    options.add(QuickMuteOption(TYPE_VIA, status.source))
                }
                for (hashtag in status.tags) {
                    options.add(QuickMuteOption(TYPE_HASHTAG, hashtag))
                }
                for (mention in status.mentions) {
                    options.add(QuickMuteOption(TYPE_MENTION, mention.screenName))
                }
                for (link in status.links) {
                    options.add(QuickMuteOption(TYPE_URL, link))
                }
                for (media in status.media) {
                    options.add(QuickMuteOption(TYPE_URL, media.browseUrl))
                }
                return options.toArray<QuickMuteOption>(arrayOfNulls<QuickMuteOption>(options.size))
            }
        }
    }
}

