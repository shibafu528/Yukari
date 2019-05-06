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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class MastodonProfileFragment : TwitterFragment(), CoroutineScope, SimpleProgressDialogFragment.OnCancelListener {

    private lateinit var progressBar: ProgressBar
    private lateinit var ivIcon: ImageView
    private lateinit var ivHeader: ImageView
    private lateinit var ivProtected: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvScreenName: TextView
    private lateinit var tvBiography: TextView
    private lateinit var tvTweetsCount: TextView
    private lateinit var tvFavoritesCount: TextView
    private lateinit var tvFollowsCount: TextView
    private lateinit var tvFollowersCount: TextView
    private lateinit var tvSince: TextView
    private lateinit var tvUserId: TextView
    private lateinit var cvTweets: CardView
    private lateinit var cvFollows: CardView
    private lateinit var cvFollowers: CardView

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
            targetUser = savedInstanceState.getSerializable(STATE_TARGET_USER) as DonUser?
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_profile, container, false)

        val toolbar = v.findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.profile)

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
        tvTweetsCount = v.findViewById(R.id.tvProfileTweetsCount)
        tvFavoritesCount = v.findViewById(R.id.tvProfileFavoritesCount)
        tvFollowsCount = v.findViewById(R.id.tvProfileFollowsCount)
        tvFollowersCount = v.findViewById(R.id.tvProfileFollowersCount)
        tvSince = v.findViewById(R.id.tvProfileSince)
        tvUserId = v.findViewById(R.id.tvProfileUserId)

        cvTweets = v.findViewById(R.id.cvProfileTweets)
        cvTweets.setOnClickListener {
            val user = targetUser ?: return@setOnClickListener

            val fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER)
            fragment.arguments = Bundle().apply {
                putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_FILTER)
                putSerializable(TweetListFragment.EXTRA_USER, currentUser)
                putString(TweetListFragment.EXTRA_TITLE, "Tweets: @" + user.screenName)
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

        outState.putSerializable(STATE_TARGET_USER, targetUser)
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

        // TODO: 適切なリンクのハンドリングをするために自前でのパースを検討する
        // あと、この方法だと最後に1行不自然な空白がある。pタグのせい？
        tvBiography.text = HtmlCompat.fromHtml(account.note, HtmlCompat.FROM_HTML_MODE_COMPACT)

        tvTweetsCount.text = account.statusesCount.toString()
        tvFavoritesCount.text = "-"
        tvFollowsCount.text = account.followingCount.toString()
        tvFollowersCount.text = account.followersCount.toString()

        val createdAt = Time().apply { parse3339(account.createdAt) }
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(createdAt.toMillis(true)))
        val totalDay = ((System.currentTimeMillis() - createdAt.toMillis(true)) / DateUtils.DAY_IN_MILLIS).toInt()
        val tpd = account.statusesCount.toFloat() / totalDay

        tvSince.text = String.format("%s (%d日, %.2ftweet/day)", dateStr, totalDay, tpd)
        tvUserId.text = "#" + user.id
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