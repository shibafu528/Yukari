package shibafu.yukari.fragment.status

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import shibafu.yukari.R
import shibafu.yukari.activity.MainActivity
import shibafu.yukari.activity.PreviewActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.activity.TraceActivity
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.common.StatusChildUI
import shibafu.yukari.common.StatusUI
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.base.ListTwitterFragment
import shibafu.yukari.fragment.tabcontent.DefaultTweetListFragment
import shibafu.yukari.fragment.tabcontent.TweetListFragment
import shibafu.yukari.media2.Media
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.util.defaultSharedPreferences
import twitter4j.GeoLocation

class StatusLinkFragment : ListTwitterFragment(), StatusChildUI {
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

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (defaultSharedPreferences.getString("pref_theme", "light").endsWith("dark")) {
            view?.setBackgroundResource(R.drawable.dialog_full_material_dark)
        } else {
            view?.setBackgroundResource(R.drawable.dialog_full_material_light)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val status = status

        // リスト要素の作成
        val list = arrayListOf<Row>()
        // URL系情報のダブり検出用
        val existsUrl = mutableSetOf<String>()
        // User系情報のダブり検出用
        val existsUserId = mutableSetOf<Long>()

        // 添付画像
        status.media.forEach { media ->
            if (existsUrl.contains(media.browseUrl)) {
                return@forEach
            }

            list += MediaRow(media)
            existsUrl += media.browseUrl
        }

        // URL
        status.links.forEach { link ->
            if (existsUrl.contains(link)) {
                return@forEach
            }

            list += LinkRow(link)
            existsUrl += link
        }

        // ハッシュタグ
        status.tags.forEach { tag ->
            list += TagRow("#" + tag)
        }

        // (Twitter) 位置情報
        if (status is TwitterStatus && status.status.geoLocation != null) {
            list += GeoLocationRow(status.status.geoLocation)
        }

        // 会話
        if (status.originStatus.inReplyToId > -1) {
            list += TraceRow()
        }

        // RTならRT者の情報
        if (status.isRepost) {
            list += UserRow(status.user)
            existsUserId += status.user.id
        }

        // 発言者の情報
        if (!existsUserId.contains(status.originStatus.user.id)) {
            list += UserRow(status.originStatus.user)
            existsUserId += status.originStatus.user.id
        }

        // メンション先の情報
        status.originStatus.mentions.forEach { mention ->
            if (existsUserId.contains(mention.id)) {
                return@forEach
            }

            list += UserRow(mention)
            existsUserId += mention.id
        }

        listAdapter = RowAdapter(activity, list)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val row = list[position]
            row.onLongClick()
        }
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        val row = l?.getItemAtPosition(position) as? Row
        row?.onClick()
    }

    override fun onUserChanged(userRecord: AuthUserRecord?) {}

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    private inner class RowAdapter(context: Context, objects: List<Row>) : ArrayAdapter<Row>(context, 0, objects) {
        private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.row_statuslink, parent, false)

            val row = getItem(position)
            if (row != null) {
                val tvContent = view.findViewById(R.id.statuslink_content) as TextView
                tvContent.text = row.label

                val ibActions = view.findViewById(R.id.statuslink_actions)
                ibActions.setOnClickListener { v ->
                    val menu = PopupMenu(context, v)
                    row.actions.forEachIndexed { index, action ->
                        val item = menu.menu.add(Menu.NONE, index, index, action.label)
                        if (action.icon != null) {
                            item.icon = action.icon
                        }
                    }
                    menu.setOnMenuItemClickListener { item ->
                        startActivity(row.actions[item.itemId].intent())
                        true
                    }
                    menu.show()
                }
                if (row.actions.isEmpty()) {
                    ibActions.visibility = View.GONE
                } else {
                    ibActions.visibility = View.VISIBLE
                }
            }

            return view
        }
    }

    private interface Row {
        val icon: Drawable?
        val label: String
        val actions: List<ExtraAction>
            get() = emptyList()

        fun onClick()
        fun onLongClick(): Boolean = false
    }

    private inner class MediaRow(val media: Media) : Row {
        override val icon: Drawable? = null
        override val label: String = media.browseUrl

        override fun onClick() {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.browseUrl), activity, PreviewActivity::class.java)
            intent.putExtra(PreviewActivity.EXTRA_STATUS, status)
            startActivity(intent)
        }

        override fun onLongClick(): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.browseUrl))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            return true
        }
    }

    private inner class LinkRow(val url: String) : Row {
        override val icon: Drawable? = null
        override val label: String = url
        override val actions: List<ExtraAction> = {
            val uri = Uri.parse(url)
            val intent = Intent(ACTION_LINK_ACCEL, uri)
            val pm = activity.packageManager
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { resolveInfo ->
                ExtraAction(resolveInfo.loadIcon(pm), resolveInfo.loadLabel(pm).toString(), {
                    val grantName = try {
                        val ai = pm.getActivityInfo(ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name), PackageManager.GET_META_DATA)
                        ai.metaData.getBoolean("can_receive_name", false)
                    } catch (e: NullPointerException) {
                        false
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }

                    val user = userRecord!!
                    Intent(ACTION_LINK_ACCEL, uri).apply {
                        setPackage(resolveInfo.activityInfo.packageName)
                        setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                        putExtra("grant_name", grantName)
                        putExtra("user_name", user.Name)
                        putExtra("user_screen_name", user.ScreenName)
                        putExtra("user_id", user.NumericId)
                        putExtra("user_profile_image_url", user.ProfileImageUrl)
                        putExtra("status_url", status!!.url)
                    }
                })
            }
        }()

        override fun onClick() {
            fun startBrowser(uri: Uri) {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }

            val uri = Uri.parse(url)
            if (uri.host.contains("www.google")) {
                val lastPathSegment = uri.lastPathSegment
                if (lastPathSegment != null && lastPathSegment == "search") {
                    val query = uri.getQueryParameter("q")
                    val ad = AlertDialog.Builder(activity)
                            .setTitle("検索URL")
                            .setMessage("検索キーワードは「$query」です。\nブラウザで開きますか？")
                            .setPositiveButton("続行") { dialog, _ ->
                                dialog.dismiss()
                                startBrowser(uri)
                            }
                            .setNegativeButton("キャンセル") { dialog, _ -> dialog.dismiss() }
                            .create()
                    ad.show()
                } else if (url.contains("matome.naver.jp/odai/2133899121334612301") || url.contains("matome.naver.jp/odai/2138315187918614201")) {
                    val ad = AlertDialog.Builder(activity)
                            .setTitle("確認")
                            .setMessage("このURLは飯テロ系まとめです。\nブラウザで開きますか？")
                            .setPositiveButton("続行") { dialog, _ ->
                                dialog.dismiss()
                                startBrowser(uri)
                            }
                            .setNegativeButton("キャンセル") { dialog, _ -> dialog.dismiss() }
                            .create()
                    ad.show()
                } else {
                    startBrowser(uri)
                }
            } else {
                startBrowser(uri)
            }
        }
    }

    private inner class TagRow(val tag: String) : Row {
        override val icon: Drawable? = null
        override val label: String = tag

        override fun onClick() {
            val ad = AlertDialog.Builder(activity)
                    .setTitle(tag)
                    .setPositiveButton("つぶやく", { dialog, _ ->
                        dialog.dismiss()

                        val intent = Intent(activity, TweetActivity::class.java)
                        intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
                        intent.putExtra(TweetActivity.EXTRA_TEXT, " " + tag)
                        startActivity(intent)
                    })
                    .setNegativeButton("検索する", { dialog, _ ->
                        dialog.dismiss()

                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, tag)
                        startActivity(intent)
                    })
                    .setNeutralButton("キャンセル", { dialog, _ -> dialog.dismiss() })
                    .create()
            ad.show()
        }
    }

    private inner class GeoLocationRow(val geo: GeoLocation) : Row {
        override val icon: Drawable? = null
        override val label: String = "位置情報: ${geo.latitude}, ${geo.longitude}"

        override fun onClick() {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${geo.latitude},${geo.longitude}"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private inner class TraceRow : Row {
        override val icon: Drawable? = null
        override val label: String = "会話をたどる"

        override fun onClick() {
            status.let { status ->
                val intent = Intent(activity, TraceActivity::class.java)
                intent.putExtra(TweetListFragment.EXTRA_USER, userRecord)
                intent.putExtra(TweetListFragment.EXTRA_TITLE, "Trace")
                intent.putExtra(DefaultTweetListFragment.EXTRA_TRACE_START, status.originStatus)
                startActivity(intent)
            }
        }
    }

    private inner class UserRow(val user: Mention) : Row {
        override var icon: Drawable? = ResourcesCompat.getDrawable(resources, R.drawable.yukatterload, null)
        override val label: String = "@" + user.screenName
        override val actions: List<ExtraAction>

        init {
            ImageLoaderTask.loadDrawable(context, user.profileImageUrl, BitmapCache.PROFILE_ICON_CACHE, { drawable ->
                icon = drawable
                if (isResumed) {
                    (listAdapter as RowAdapter).notifyDataSetChanged()
                }
            })

            val replyAction = ExtraAction(null, "返信") {
                Intent(activity, TweetActivity::class.java).apply {
                    putExtra(TweetActivity.EXTRA_USER, userRecord)
                    putExtra(TweetActivity.EXTRA_TEXT, "@" + user.screenName + " ")
                    putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
                }
            }

            if (user is TwitterUser) {
                val dmAction = ExtraAction(null, "DMを送る") {
                    Intent(activity, TweetActivity::class.java).apply {
                        putExtra(TweetActivity.EXTRA_USER, userRecord)
                        putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM)
                        putExtra(TweetActivity.EXTRA_IN_REPLY_TO, id)
                        putExtra(TweetActivity.EXTRA_DM_TARGET_SN, user.screenName)
                    }
                }
                actions = listOf(replyAction, dmAction)
            } else {
                actions = listOf(replyAction)
            }
        }

        override fun onClick() {
            val intent = ProfileActivity.newIntent(activity, userRecord, Uri.parse(user.url))
            startActivity(intent)
        }
    }

    private data class ExtraAction(
            val icon: Drawable?,
            val label: String,
            val intent: () -> Intent
    )

    companion object {
        private const val ACTION_LINK_ACCEL = "shibafu.yukari.ACTION_LINK_ACCEL"
    }
}