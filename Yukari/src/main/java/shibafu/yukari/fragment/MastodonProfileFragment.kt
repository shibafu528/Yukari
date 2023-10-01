package shibafu.yukari.fragment

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.util.Xml
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.appbar.AppBarLayout
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Accounts
import com.sys1yagi.mastodon4j.api.method.Public
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import shibafu.yukari.R
import shibafu.yukari.activity.PreviewActivity2
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.common.TabType
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.common.span.UserProfileSpan
import shibafu.yukari.core.App
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.fragment.base.YukariBaseFragment
import shibafu.yukari.fragment.tabcontent.TimelineFragment
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory
import shibafu.yukari.mastodon.entity.DonUser
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import shibafu.yukari.view.ProfileButton
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class MastodonProfileFragment : YukariBaseFragment(), CoroutineScope, SimpleAlertDialogFragment.OnDialogChoseListener, SimpleProgressDialogFragment.OnCancelListener, Toolbar.OnMenuItemClickListener, ColorPickerDialogFragment.ColorPickerCallback {

    private lateinit var progressBar: ProgressBar
    private lateinit var ivIcon: ImageView
    private lateinit var ivHeader: ImageView
    private lateinit var ivProtected: ImageView
    private lateinit var ivProfileUserColor: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvScreenName: TextView
    private lateinit var tvBiography: TextView
    private lateinit var pbTweets: ProfileButton
    private lateinit var pbPinnedToots: ProfileButton
    private lateinit var pbFollows: ProfileButton
    private lateinit var pbFollowers: ProfileButton
    private lateinit var tvSince: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvDetailSubHeader: TextView
    private lateinit var cvNotice: CardView
    private lateinit var cvMoved: CardView
    private lateinit var ivMovedIcon: ImageView
    private lateinit var tvMovedName: TextView
    private lateinit var tvMovedScreenName: TextView
    private lateinit var cvFields: CardView
    private lateinit var llFields: LinearLayout

    private lateinit var currentUser: AuthUserRecord
    private lateinit var targetUrl: Uri

    private val job = Job()
    private val xppFactory = XmlPullParserFactory.newInstance().apply {
        isValidating = false
        setFeature(Xml.FEATURE_RELAXED, true)
    }

    private var targetUser: DonUser? = null
    private var targetPinnedCount: String = ""

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments ?: Bundle.EMPTY
        currentUser = args.getSerializable(EXTRA_USER) as AuthUserRecord
        targetUrl = args.getParcelable(EXTRA_TARGET_URL)!!

        if (savedInstanceState != null) {
            targetUser = savedInstanceState.getParcelable(STATE_TARGET_USER)
            targetPinnedCount = savedInstanceState.getString(STATE_TARGET_PINNED_COUNT, "")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_mastodon_profile, container, false)

        val toolbar = v.findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.mastodon_profile)
        toolbar.setOnMenuItemClickListener(this)

        progressBar = v.findViewById(R.id.progressBar)

        ivIcon = v.findViewById(R.id.ivProfileIcon)
        ivIcon.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            startActivity(PreviewActivity2.newIntent(requireContext(), Uri.parse(user.biggerProfileImageUrl)))
        }

        ivHeader = v.findViewById(R.id.ivProfileHeader)
        ivHeader.setOnClickListener {
            val account = targetUser?.account ?: return@setOnClickListener

            if (account.header.isEmpty()) {
                return@setOnClickListener
            }

            startActivity(PreviewActivity2.newIntent(requireContext(), Uri.parse(account.header)))
        }

        ivProtected = v.findViewById(R.id.ivProfileProtected)
        ivProfileUserColor = v.findViewById(R.id.ivProfileUserColor)
        tvName = v.findViewById(R.id.tvProfileName)
        tvScreenName = v.findViewById(R.id.tvProfileScreenName)
        tvBiography = v.findViewById(R.id.tvProfileBio)
        tvSince = v.findViewById(R.id.tvProfileSince)
        tvUserId = v.findViewById(R.id.tvProfileUserId)
        tvDetailSubHeader = v.findViewById(R.id.tvProfileDetailSubHeader)

        pbTweets = v.findViewById(R.id.cvProfileTweets)
        pbTweets.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER)
            fragment.arguments = Bundle().apply {
                putInt(TimelineFragment.EXTRA_MODE, TabType.TABTYPE_FILTER)
                putSerializable(TimelineFragment.EXTRA_USER, currentUser)
                putString(TimelineFragment.EXTRA_TITLE, "Toots: @" + user.screenName)
                putString(TimelineFragment.EXTRA_FILTER_QUERY, "from user:\"${currentUser.ScreenName}/${user.screenName}\"")
            }

            val fm = fragmentManager ?: return@setOnClickListener
            fm.beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }

        pbPinnedToots = v.findViewById(R.id.cvProfilePinnedToots)
        pbPinnedToots.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER)
            fragment.arguments = Bundle().apply {
                putInt(TimelineFragment.EXTRA_MODE, TabType.TABTYPE_FILTER)
                putSerializable(TimelineFragment.EXTRA_USER, currentUser)
                putString(TimelineFragment.EXTRA_TITLE, "Pinned: @" + user.screenName)
                putString(TimelineFragment.EXTRA_FILTER_QUERY, "from don_user_pinned:\"${currentUser.ScreenName}/${user.screenName}\"")
            }

            val fm = fragmentManager ?: return@setOnClickListener
            fm.beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }

        pbFollows = v.findViewById(R.id.cvProfileFollows)
        pbFollows.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = MastodonUserListFragment.newFollowingListInstance(currentUser, "Follow: @${user.screenName}", user.id)

            val fm = fragmentManager ?: return@setOnClickListener
            fm.beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }

        pbFollowers = v.findViewById(R.id.cvProfileFollowers)
        pbFollowers.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = MastodonUserListFragment.newFollowerListInstance(currentUser, "Follower: @${user.screenName}", user.id)

            val fm = fragmentManager ?: return@setOnClickListener
            fm.beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }

        cvMoved = v.findViewById(R.id.cvProfileMoved)
        cvMoved.setOnClickListener {
            val user = targetUser?.account?.moved ?: return@setOnClickListener

            startActivity(ProfileActivity.newIntent(requireContext(), currentUser, Uri.parse(user.url)))
        }

        ivMovedIcon = cvMoved.findViewById(R.id.user_icon)
        tvMovedName = cvMoved.findViewById(R.id.user_name)
        tvMovedScreenName = cvMoved.findViewById(R.id.user_sn)

        cvNotice = v.findViewById(R.id.cvProfileNotice)
        cvFields = v.findViewById(R.id.cvProfileFields)
        llFields = v.findViewById(R.id.llProfileFields)

        val btnFollowManage = v.findViewById<Button>(R.id.btnProfileFollow)
        btnFollowManage.setOnClickListener {
            showToast("まだ未対応です")
        }

        val appBarLayout = v.findViewById<AppBarLayout>(R.id.appBarLayout)
        appBarLayout.addOnOffsetChangedListener(ProfileFragment.AppBarOffsetChangedCallback(ivIcon))

        return v
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val target = targetUser
        if (target == null) {
            loadProfile()
        } else {
            showProfile(target)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(STATE_TARGET_USER, targetUser)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_REQUEST_LOAD_FAILED -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    startActivity(Intent(Intent.ACTION_VIEW, targetUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                requireActivity().finish()
            }
        }
    }

    override fun onProgressDialogCancel(requestCode: Int, dialog: DialogInterface?) {
        when (requestCode) {
            DIALOG_REQUEST_LOAD -> requireActivity().finish()
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        val user = targetUser
        if (user == null) {
            Toast.makeText(context, "何か調子が悪いみたいです。画面を一度開き直してみてください。", Toast.LENGTH_SHORT).show()
            return true
        }

        if (item == null) {
            return false
        }

        when (item.itemId) {
            R.id.action_send_mention -> {
                val intent = Intent(requireContext(), TweetActivity::class.java)
                intent.putExtra(TweetActivity.EXTRA_USER, currentUser)
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
                intent.putExtra(TweetActivity.EXTRA_TEXT, "@${targetUser?.screenName} ")
                startActivity(intent)
                return true
            }
            R.id.action_browser -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(user.url))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return true
            }
            R.id.action_set_color_label -> {
                val dialogFragment = ColorPickerDialogFragment.newInstance(getTargetUserColor(), FRAGMENT_TAG_SET_COLOR_LABEL)
                dialogFragment.setTargetFragment(this, DIALOG_REQUEST_SET_COLOR_LABEL)
                dialogFragment.show(parentFragmentManager, FRAGMENT_TAG_SET_COLOR_LABEL)
                return true
            }
        }

        return false
    }

    override fun onColorPicked(color: Int, tag: String?) {
        val user = targetUser
        if (user == null) {
            Toast.makeText(context, "カラーラベル設定に失敗しました\"", Toast.LENGTH_SHORT).show()
            return
        }

        App.getInstance(requireContext()).userExtrasManager.setColor(user.identicalUrl, color)
        showProfile(user)
    }

    private fun loadProfile() = launch {
        fragmentManager?.let {
            val dialog = it.findFragmentByTag(FRAGMENT_TAG_LOAD)
            if (dialog is SimpleProgressDialogFragment) {
                dialog.dismiss()
            }
        }

        val progressDialog = SimpleProgressDialogFragment.Builder(DIALOG_REQUEST_LOAD).build()
        progressDialog.setTargetFragment(this@MastodonProfileFragment, DIALOG_REQUEST_LOAD)
        progressDialog.show(parentFragmentManager, FRAGMENT_TAG_LOAD)

        val result = async(Dispatchers.IO) {
            try {
                val service = getTwitterServiceAwait() ?: return@async null

                val api = service.getProviderApi(currentUser) ?: return@async null
                val client = api.getApiClient(currentUser) as? MastodonClient ?: return@async null

                val result = Public(client).getSearch(targetUrl.toString(), true).execute()
                val account = result.accounts.firstOrNull() ?: return@async null

                val pinnedResult = Accounts(client).getStatuses(account.id, pinned = true).execute()
                val pinnedCount = run {
                    val link = pinnedResult.link

                    if (link != null && link.nextPath.isNotEmpty()) {
                        "${pinnedResult.part.size}+"
                    } else {
                        pinnedResult.part.size.toString()
                    }
                }

                DonUser(account) to pinnedCount
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                null
            }
        }.await()

        progressDialog.dismiss()

        Log.d(MastodonProfileFragment::class.java.simpleName, "Load done.")

        if (result == null) {
            val dialog = SimpleAlertDialogFragment.Builder(DIALOG_REQUEST_LOAD_FAILED)
                    .setMessage("ユーザー情報の取得に失敗しました。代わりにブラウザで開きますか？")
                    .setPositive("OK")
                    .setNegative("キャンセル")
                    .build()
            dialog.setTargetFragment(this@MastodonProfileFragment, DIALOG_REQUEST_LOAD_FAILED)
            dialog.show(parentFragmentManager, FRAGMENT_TAG_LOAD_FAILED)
        } else {
            val (user, pinnedCount) = result

            targetUser = user
            targetPinnedCount = pinnedCount

            if (isResumed) {
                showProfile(user)
            }
        }
    }

    private fun showProfile(user: DonUser) {
        val account = user.account!!

        val service = twitterService
        if (service != null) {
            // 表示ユーザーが自分のアカウントであれば操作ユーザー上書き
            currentUser = service.users.firstOrNull { it.ScreenName == user.screenName } ?: currentUser
        }

        progressBar.visibility = View.INVISIBLE

        ImageLoaderTask.loadProfileIcon(requireContext(), ivIcon, user.biggerProfileImageUrl)

        if (account.header.isNotEmpty()) {
            ImageLoaderTask.loadBitmap(requireContext(), ivHeader, account.header)
        }

        tvName.text = user.name
        tvScreenName.text = "@" + user.screenName
        ivProfileUserColor.setBackgroundColor(getTargetUserColor())

        if (user.isProtected) {
            ivProtected.visibility = View.VISIBLE
        } else {
            ivProtected.visibility = View.GONE
        }

        if (currentUser.Provider.host == user.host) {
            cvNotice.visibility = View.GONE
        } else {
            cvNotice.visibility = View.VISIBLE
        }

        tvBiography.text = parseHtml(account.note)
        tvBiography.movementMethod = LinkMovementMethod.getInstance()

        pbTweets.count = account.statusesCount.toString()
        pbPinnedToots.count = targetPinnedCount
        pbFollows.count = account.followingCount.toString()
        pbFollowers.count = account.followersCount.toString()

        val movedAccount = account.moved
        if (movedAccount == null) {
            cvMoved.visibility = View.GONE
        } else {
            val movedUser = DonUser(movedAccount)

            ImageLoaderTask.loadProfileIcon(requireContext(), ivMovedIcon, movedUser.biggerProfileImageUrl)
            tvMovedName.text = movedUser.name
            tvMovedScreenName.text = "@" + movedUser.screenName

            cvMoved.visibility = View.VISIBLE
        }

        if (account.fields.isEmpty()) {
            cvFields.visibility = View.GONE
        } else {
            cvFields.visibility = View.VISIBLE
            llFields.removeAllViews()

            val density = resources.displayMetrics.density
            val verifiedColorId = AttrUtil.resolveAttribute(requireActivity().theme, R.attr.mastodonVerifiedColor)
            val verifiedColor = ResourcesCompat.getColor(resources, verifiedColorId, requireActivity().theme)

            account.fields.forEachIndexed { index, field ->
                if (index != 0) {
                    val ivDivider = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.roundToInt()).apply {
                            topMargin = (density * 8).roundToInt()
                            bottomMargin = (density * 8).roundToInt()
                        }

                        scaleType = ImageView.ScaleType.FIT_XY
                        setImageResource(android.R.drawable.divider_horizontal_textfield)
                    }

                    llFields.addView(ivDivider)
                }

                val tvName = TextView(context, null, R.style.TextAppearance_AppCompat).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                    text = field.name
                    typeface = Typeface.DEFAULT_BOLD

                    if (field.verifiedAt != null) {
                        text = "✓ " + field.name
                        setTextColor(verifiedColor)
                    }
                }
                llFields.addView(tvName)

                val tvValue = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (density * 2).roundToInt()
                    }

                    text = parseHtml(field.value)
                    movementMethod = LinkMovementMethod.getInstance()
                }
                llFields.addView(tvValue)
            }
        }

        val createdAt = ZonedDateTime.parse(account.createdAt)
        val dateStr = createdAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        val totalDay = createdAt.until(ZonedDateTime.now(), ChronoUnit.DAYS)
        val tpd = account.statusesCount.toFloat() / totalDay

        tvDetailSubHeader.text = "in ${currentUser.Provider.host}"
        tvSince.text = String.format("%s (%d日, %.2ftoots/day)", dateStr, totalDay, tpd)
        tvUserId.text = "#${user.id}"
    }

    private fun parseHtml(html: String): CharSequence {
        val ssb = SpannableStringBuilder()

        val xpp = xppFactory.newPullParser()
        xpp.setInput(StringReader("<div>$html</div>"))

        var eventType = xpp.eventType
        var linkMarker: LinkMarker? = null
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (xpp.name) {
                        "br" -> ssb.append("\n")
                        "p" -> {
                            // 2つ目以降の段落開始前。空行を差し込む。
                            if (ssb.isNotEmpty()) {
                                ssb.append("\n\n")
                            }
                        }
                        "a" -> {
                            val href = xpp.getAttributeValue(null, "href").orEmpty()
                            val classes = xpp.getAttributeValue(null, "class").orEmpty()
                            linkMarker = when {
                                classes.contains("u-url") -> LinkMarker.Mention(ssb.length, href)
                                classes.contains("hashtag") -> LinkMarker.HashTag(ssb.length, href)
                                else -> LinkMarker.Standard(ssb.length, href)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (xpp.name) {
                        "a" -> {
                            if (linkMarker != null) {
                                val span = when (linkMarker) {
                                    is LinkMarker.Standard -> URLSpan(linkMarker.href)
                                    is LinkMarker.Mention -> UserProfileSpan(currentUser, linkMarker.href)
                                    is LinkMarker.HashTag -> URLSpan(linkMarker.href)
                                }
                                ssb.setSpan(span, linkMarker.start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                linkMarker = null
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> ssb.append(xpp.text)
            }

            eventType = xpp.next()
        }

        return ssb
    }

    private fun getTargetUserColor(): Int {
        val user = targetUser ?: return 0
        return App.getInstance(requireContext()).userExtrasManager.userExtras.find { user.identicalUrl == it.id }?.color ?: 0
    }

    private sealed class LinkMarker(val start: Int) {
        class Standard(start: Int, val href: String) : LinkMarker(start)
        class Mention(start: Int, val href: String) : LinkMarker(start)
        class HashTag(start: Int, val href: String) : LinkMarker(start)
    }

    companion object {
        const val EXTRA_USER = "user"
        const val EXTRA_TARGET_URL = "targetUrl"

        private const val STATE_TARGET_USER = "targetUser"
        private const val STATE_TARGET_PINNED_COUNT = "targetPinnedCount"

        private const val FRAGMENT_TAG_LOAD = "loadProgress"
        private const val FRAGMENT_TAG_LOAD_FAILED = "loadFailedAlert"
        private const val FRAGMENT_TAG_SET_COLOR_LABEL = "setColorLabel"

        private const val DIALOG_REQUEST_LOAD = 0
        private const val DIALOG_REQUEST_LOAD_FAILED = 1
        private const val DIALOG_REQUEST_SET_COLOR_LABEL = 2

        fun newInstance(user: AuthUserRecord, targetUrl: Uri): MastodonProfileFragment {
            return MastodonProfileFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_USER, user)
                    putParcelable(EXTRA_TARGET_URL, targetUrl)
                }
            }
        }
    }
}