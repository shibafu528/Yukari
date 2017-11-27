package shibafu.yukari.fragment.tabcontent

import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.method.Public
import okhttp3.OkHttpClient
import shibafu.yukari.R
import shibafu.yukari.common.TabType
import shibafu.yukari.common.TweetAdapter
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.base.ListTwitterFragment
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.StringUtil

/**
 * 時系列順に要素を並べて表示するタブの基底クラス
 */
open class TimelineFragment : ListTwitterFragment(), TimelineTab, SwipeRefreshLayout.OnRefreshListener {
    var title: String = ""
    var mode: Int = 0

    protected val statuses: MutableList<Status> = arrayListOf()
    protected val users: MutableList<AuthUserRecord> = arrayListOf()

    protected var statusAdapter: TweetAdapter? = null

    // Capacity
    private var statusCapacity: Int = 256

    // SwipeRefreshLayout
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        when (mode) {
            TabType.TABTYPE_TRACE, TabType.TABTYPE_DM ->
                return super.onCreateView(inflater, container, savedInstanceState)
        }
        val v = inflater!!.inflate(R.layout.fragment_swipelist, container, false)

        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout) as SwipeRefreshLayout?
        swipeRefreshLayout?.setColorSchemeResources(AttrUtil.resolveAttribute(context.theme, R.attr.colorPrimary))
        swipeRefreshLayout?.setOnRefreshListener(this)

        return v
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusAdapter = TweetAdapter(context, users, null, statuses)
        listAdapter = statusAdapter
    }

    override fun onDetach() {
        super.onDetach()
        listAdapter = null
        statusAdapter = null
    }

    override fun isCloseable(): Boolean = false

    override fun scrollToTop() {
        try {
            listView?.setSelection(0)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToBottom() {
        try {
            val count = listAdapter?.count ?: 1
            listView?.setSelection(count - 1)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToOldestUnread() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun scrollToPrevPage() {
        try {
            listView?.let {
                it.smoothScrollBy(-it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToNextPage() {
        try {
            listView?.let {
                it.smoothScrollBy(it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onServiceConnected() {
        // ユーザ情報のバインド
        if (users.isEmpty()) {
            users += twitterService.users
        }

        // TL初期容量の決定
        statusCapacity = users.size * CAPACITY_INITIAL_FACTOR

        if (statusAdapter != null) {
            statusAdapter?.setUserExtras(twitterService.userExtras)
            statusAdapter?.setStatusManager(twitterService.statusManager)
        }

        // とりあえずmikutter mastodonを出す
        object : AsyncTask<Any, Any, List<DonStatus>>() {
            override fun doInBackground(vararg params: Any?): List<DonStatus> {
                val donClient = MastodonClient.Builder("social.mikutter.hachune.net",
                        OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().addHeader("User-Agent", StringUtil.getVersionInfo(context)).build()) },
                        Gson()).build()
                val public = Public(donClient)
                val res = public.getLocalPublic().execute()
                return res.part.map { DonStatus(it, twitterService.primaryUser) }.sortedByDescending { it.id }
            }

            override fun onPostExecute(result: List<DonStatus>?) {
                super.onPostExecute(result)
                result?.forEach {
                    statuses.add(0, it)
                    statusAdapter?.notifyDataSetChanged()
                }
            }
        }.execute()
    }

    override fun onServiceDisconnected() {}

    override fun onRefresh() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        /** TL容量の初期化係数 */
        private const val CAPACITY_INITIAL_FACTOR = 256
    }
}

/*
  TODO
  - AuthUserRecordでTwitter以外のユーザ情報は賄えるのか？
 */