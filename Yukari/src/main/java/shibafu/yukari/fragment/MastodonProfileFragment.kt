package shibafu.yukari.fragment

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v4.text.HtmlCompat
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.text.format.Time
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Public
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shibafu.yukari.R
import shibafu.yukari.activity.PreviewActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.common.TabType
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.fragment.base.TwitterFragment
import shibafu.yukari.fragment.tabcontent.TimelineFragment
import shibafu.yukari.fragment.tabcontent.TweetListFragment
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory
import shibafu.yukari.mastodon.entity.DonUser
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.view.ProfileButton
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class MastodonProfileFragment : TwitterFragment(), CoroutineScope, SimpleProgressDialogFragment.OnCancelListener, Toolbar.OnMenuItemClickListener {

    private lateinit var progressBar: ProgressBar
    private lateinit var ivIcon: ImageView
    private lateinit var ivHeader: ImageView
    private lateinit var ivProtected: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvScreenName: TextView
    private lateinit var tvBiography: TextView
    private lateinit var pbTweets: ProfileButton
    private lateinit var pbFollows: ProfileButton
    private lateinit var pbFollowers: ProfileButton
    private lateinit var tvSince: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvDetailSubHeader: TextView
    private lateinit var cvTweets: CardView
    private lateinit var cvFollows: CardView
    private lateinit var cvFollowers: CardView
    private lateinit var cvNotice: CardView

    private lateinit var currentUser: AuthUserRecord
    private lateinit var targetUrl: Uri

    private val job = Job()

    private var targetUser: DonUser? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments ?: Bundle.EMPTY
        currentUser = args.getSerializable(EXTRA_USER) as AuthUserRecord
        targetUrl = args.getParcelable(EXTRA_TARGET_URL) as Uri

        if (savedInstanceState != null) {
            targetUser = savedInstanceState.getParcelable(STATE_TARGET_USER)
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

            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(user.biggerProfileImageUrl), requireContext(), PreviewActivity::class.java))
        }

        ivHeader = v.findViewById(R.id.ivProfileHeader)
        ivHeader.setOnClickListener {
            val account = targetUser?.account ?: return@setOnClickListener

            if (account.header.isEmpty()) {
                return@setOnClickListener
            }

            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(account.header), requireContext(), PreviewActivity::class.java))
        }

        ivProtected = v.findViewById(R.id.ivProfileProtected)
        tvName = v.findViewById(R.id.tvProfileName)
        tvScreenName = v.findViewById(R.id.tvProfileScreenName)
        tvBiography = v.findViewById(R.id.tvProfileBio)
        pbTweets = v.findViewById(R.id.cvProfileTweets)
        pbFollows = v.findViewById(R.id.cvProfileFollows)
        pbFollowers = v.findViewById(R.id.cvProfileFollowers)
        tvSince = v.findViewById(R.id.tvProfileSince)
        tvUserId = v.findViewById(R.id.tvProfileUserId)
        tvDetailSubHeader = v.findViewById(R.id.tvProfileDetailSubHeader)

        cvTweets = v.findViewById(R.id.cvProfileTweets)
        cvTweets.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER)
            fragment.arguments = Bundle().apply {
                putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_FILTER)
                putSerializable(TweetListFragment.EXTRA_USER, currentUser)
                putString(TweetListFragment.EXTRA_TITLE, "Toots: @" + user.screenName)
                putString(TimelineFragment.EXTRA_FILTER_QUERY, "from user:\"${currentUser.ScreenName}/${user.screenName}\"")
            }

            val fm = fragmentManager ?: return@setOnClickListener
            fm.beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }

        cvFollows = v.findViewById(R.id.cvProfileFollows)
        cvFollowers = v.findViewById(R.id.cvProfileFollowers)
        cvNotice = v.findViewById(R.id.cvProfileNotice)

        // TODO: まだフォロー管理できないのでさよなら
        val btnFollowManage = v.findViewById<Button>(R.id.btnProfileFollow)
        btnFollowManage.visibility = View.GONE

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

    override fun onServiceConnected() {
        // TODO
    }

    override fun onServiceDisconnected() {}

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
            R.id.action_browser -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(user.url))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return true
            }
        }

        return false
    }

    private suspend fun getTwitterServiceAwait(): TwitterService? {
        while (!isTwitterServiceBound) {
            delay(100)
        }

        return twitterService
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
        progressDialog.show(fragmentManager, FRAGMENT_TAG_LOAD)

        val user = async(Dispatchers.IO) {
            try {
                val service = getTwitterServiceAwait() ?: return@async null

                val api = service.getProviderApi(currentUser) ?: return@async null
                val client = api.getApiClient(currentUser) as? MastodonClient ?: return@async null

                val result = Public(client).getSearch(targetUrl.toString(), true).execute()
                val account = result.accounts.firstOrNull() ?: return@async null

                DonUser(account)
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                null
            }
        }.await()

        progressDialog.dismiss()

        Log.d(MastodonProfileFragment::class.java.simpleName, "Load done.")

        if (user == null) {
            Toast.makeText(requireActivity(), "ユーザー情報の取得に失敗しました", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        } else {
            targetUser = user

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

        // TODO: 適切なリンクのハンドリングをするために自前でのパースを検討する
        // あと、この方法だと最後に1行不自然な空白がある。pタグのせい？
        tvBiography.text = HtmlCompat.fromHtml(account.note, HtmlCompat.FROM_HTML_MODE_COMPACT)
        tvBiography.movementMethod = LinkMovementMethod.getInstance()

        pbTweets.count = account.statusesCount.toString()
        pbFollows.count = account.followingCount.toString()
        pbFollowers.count = account.followersCount.toString()

        val createdAt = Time().apply { parse3339(account.createdAt) }
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(createdAt.toMillis(true)))
        val totalDay = ((System.currentTimeMillis() - createdAt.toMillis(true)) / DateUtils.DAY_IN_MILLIS).toInt()
        val tpd = account.statusesCount.toFloat() / totalDay

        tvDetailSubHeader.text = "in ${currentUser.Provider.host}"
        tvSince.text = String.format("%s (%d日, %.2ftoots/day)", dateStr, totalDay, tpd)
        tvUserId.text = "#${user.id}"
    }

    companion object {
        const val EXTRA_USER = "user"
        const val EXTRA_TARGET_URL = "targetUrl"

        private const val STATE_TARGET_USER = "targetUser"

        private const val FRAGMENT_TAG_LOAD = "loadProgress"

        private const val DIALOG_REQUEST_LOAD = 0

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