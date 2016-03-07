package shibafu.yukari.fragment.status

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.text.ClipboardManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import shibafu.yukari.activity.MuteActivity
import shibafu.yukari.activity.StatusActivity
import shibafu.yukari.af2015.R
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.fragment.ListRegisterDialogFragment
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.fragment.base.ListTwitterFragment
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.showToast
import java.util.*
import java.util.regex.Pattern

/**
 * Created by shibafu on 2016/02/05.
 */
public class StatusActionFragment : ListTwitterFragment(), AdapterView.OnItemClickListener, SimpleAlertDialogFragment.OnDialogChoseListener {
    companion object {
        private const val REQUEST_DELETE = 0
    }

    private val status: PreformedStatus by lazy { arguments.getSerializable(StatusActivity.EXTRA_STATUS) as PreformedStatus }
    private val user: AuthUserRecord by lazy { arguments.getSerializable(StatusActivity.EXTRA_USER) as AuthUserRecord }

    private var itemList: List<StatusAction> = emptyList()

    private val itemTemplates: List<Pair<StatusAction, () -> Boolean>> = listOf(
            Action("ブラウザで開く") {
                startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_VIEW, Uri.parse(TwitterUtil.getTweetURL(status)))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        null))
            } visibleWhen { true },

            Action("パーマリンクをコピー") {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .text = TwitterUtil.getTweetURL(status)
                showToast("リンクをコピーしました")
            } visibleWhen { true },

            Action("ブックマークに追加") {
                twitterService.database.updateRecord(Bookmark(status))
                showToast("ブックマークしました")
            } visibleWhen { status !is Bookmark },

            Action("リストへ追加/削除") {
                ListRegisterDialogFragment.newInstance(status.sourceUser).let {
                    it.setTargetFragment(this, 0)
                    it.show(childFragmentManager, "register")
                }
            } visibleWhen { true },

            Action("ミュートする") {
                MuteMenuDialogFragment.newInstance(status, this).show(childFragmentManager, "mute")
            } visibleWhen { true },

            Action("ツイートを削除") {
                val dialog = SimpleAlertDialogFragment.newInstance(REQUEST_DELETE, "確認", "ツイートを削除しますか？", "OK", "キャンセル")
                dialog.setTargetFragment(this, REQUEST_DELETE)
                dialog.show(fragmentManager, "delete")
            } visibleWhen { status is Bookmark || status.user.id == user.NumericId }
    )

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (defaultSharedPreferences.getString("pref_theme", "light")) {
            "light" -> view?.setBackgroundResource(R.drawable.dialog_full_holo_light)
            "dark" -> view?.setBackgroundResource(R.drawable.dialog_full_holo_dark)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val plugins: List<TwiccaPluginAction> =
                if (status.sourceUser.isProtected) {
                    emptyList()
                } else {
                    val query = Intent("jp.r246.twicca.ACTION_SHOW_TWEET").addCategory(Intent.CATEGORY_DEFAULT)

                    activity.packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
                        .sortedWith(ResolveInfo.DisplayNameComparator(activity.packageManager))
                        .map { TwiccaPluginAction(it) }
                }

        itemList = itemTemplates.filter { it.second() }.map { it.first } + plugins

        listAdapter = ArrayAdapter<StatusAction>(activity, android.R.layout.simple_list_item_1, itemList)
        listView.onItemClickListener = this
        listView.isStackFromBottom = defaultSharedPreferences.getBoolean("pref_bottom_stack", false)
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        itemList[position].onClick()
    }

    override fun onDialogChose(requestCode: Int, which: Int) {
        when (requestCode) {
            REQUEST_DELETE -> if (which == DialogInterface.BUTTON_POSITIVE) {
                ParallelAsyncTask.executeParallel {
                    if (this.status is Bookmark) {
                        twitterService.database.deleteRecord(this.status as Bookmark)
                    } else {
                        twitterService.destroyStatus(user, this.status.id)
                    }
                }
                activity.finish()
            }
        }
    }

    private fun onSelectedMuteOption(muteOption: QuickMuteOption) {
        startActivity(muteOption.toIntent(activity))
    }

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
        override val label: String = resolveInfo.activityInfo.loadLabel(activity.packageManager).toString()

        override fun onClick() {
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
                .putExtra("user_profile_image_url", status.user.profileImageURL)
                .putExtra("user_profile_image_url_mini", status.user.miniProfileImageURL)
                .putExtra("user_profile_image_url_normal", status.user.originalProfileImageURL)
                .putExtra("user_profile_image_url_bigger", status.user.biggerProfileImageURL)

            if (status.inReplyToStatusId > -1) {
                intent.putExtra("in_reply_to_status_id", status.inReplyToStatusId)
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
            val muteOptions = QuickMuteOption.fromStatus(arguments.getSerializable("status") as PreformedStatus)
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

            fun newInstance(status: PreformedStatus, target: StatusActionFragment): MuteMenuDialogFragment {
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
            when (type) {
                TYPE_TEXT -> return "本文"
                TYPE_USER_NAME -> return "ユーザー名($value)"
                TYPE_SCREEN_NAME -> return "スクリーンネーム(@$value)"
                TYPE_USER_ID -> return "ユーザーID($value)"
                TYPE_VIA -> return "クライアント名($value)"
                TYPE_HASHTAG -> return "#" + value
                TYPE_MENTION -> return "@" + value
                else -> return value
            }
        }

        fun toIntent(context: Context): Intent {
            var query = value
            var which = MuteConfig.SCOPE_TEXT
            var match = MuteConfig.MATCH_EXACT
            when (type) {
                TYPE_TEXT -> which = MuteConfig.SCOPE_TEXT
                TYPE_USER_NAME -> which = MuteConfig.SCOPE_USER_NAME
                TYPE_SCREEN_NAME -> which = MuteConfig.SCOPE_USER_SN
                TYPE_USER_ID -> which = MuteConfig.SCOPE_USER_ID
                TYPE_VIA -> which = MuteConfig.SCOPE_VIA
                TYPE_HASHTAG -> {
                    query = "[#＃]" + value
                    match = MuteConfig.MATCH_REGEX
                }
                TYPE_MENTION -> {
                    query = "@" + value
                    match = MuteConfig.MATCH_PARTIAL
                }
                TYPE_URL -> match = MuteConfig.MATCH_PARTIAL
            }
            return Intent(context, MuteActivity::class.java).putExtra(MuteActivity.EXTRA_QUERY, query).putExtra(MuteActivity.EXTRA_SCOPE, which).putExtra(MuteActivity.EXTRA_MATCH, match)
        }

        companion object {
            val TYPE_TEXT = 0
            val TYPE_USER_NAME = 1
            val TYPE_SCREEN_NAME = 2
            val TYPE_USER_ID = 3
            val TYPE_VIA = 4
            val TYPE_HASHTAG = 5
            val TYPE_MENTION = 6
            val TYPE_URL = 7

            fun fromStatus(status: PreformedStatus): Array<QuickMuteOption> {
                val options = ArrayList<QuickMuteOption>()
                options.add(QuickMuteOption(TYPE_TEXT, status.text))
                options.add(QuickMuteOption(TYPE_USER_NAME, status.sourceUser.name))
                options.add(QuickMuteOption(TYPE_SCREEN_NAME, status.sourceUser.screenName))
                options.add(QuickMuteOption(TYPE_USER_ID, status.sourceUser.id.toString()))
                options.add(QuickMuteOption(TYPE_VIA, status.source))
                for (hashtagEntity in status.hashtagEntities) {
                    options.add(QuickMuteOption(TYPE_HASHTAG, hashtagEntity.text))
                }
                for (userMentionEntity in status.userMentionEntities) {
                    options.add(QuickMuteOption(TYPE_MENTION, userMentionEntity.screenName))
                }
                for (urlEntity in status.urlEntities) {
                    options.add(QuickMuteOption(TYPE_URL, urlEntity.expandedURL))
                }
                for (linkMedia in status.mediaLinkList) {
                    options.add(QuickMuteOption(TYPE_URL, linkMedia.browseURL))
                }
                return options.toArray<QuickMuteOption>(arrayOfNulls<QuickMuteOption>(options.size))
            }
        }
    }
}

