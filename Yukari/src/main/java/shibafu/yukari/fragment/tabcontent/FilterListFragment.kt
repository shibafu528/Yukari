package shibafu.yukari.fragment.tabcontent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.util.LongSparseArray
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import shibafu.yukari.af2015.R
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.compiler.FilterCompilerException
import shibafu.yukari.filter.compiler.QueryCompiler
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.ExceptionStatus
import shibafu.yukari.twitter.statusimpl.FakeStatus
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus
import shibafu.yukari.twitter.statusimpl.MetaStatus
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusimpl.RestCompletedStatus
import shibafu.yukari.twitter.statusmanager.StatusListener
import shibafu.yukari.twitter.statusmanager.StatusManager
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.putDebugLog
import twitter4j.DirectMessage
import twitter4j.Status

/**
 * Created by shibafu on 15/06/06.
 */
public class FilterListFragment : TweetListFragment(), StatusListener {
    companion object {
        const val EXTRA_FILTER_QUERY = "filterQuery"
    }

    private var filterRawQuery = ""
    private var filterQuery: FilterQuery? = null

    private val loadingTaskKeys = arrayListOf<Long>()
    private val queryingLoadMarkers = LongSparseArray<Long>() // TaskKey, LoadMarkerStatus.Id

    private val onReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        filterRawQuery = arguments?.getString(EXTRA_FILTER_QUERY) ?: ""
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        removeFooter()
    }

    override fun onStart() {
        super.onStart()
        activity.registerReceiver(onReloadReceiver, IntentFilter(TwitterService.RELOADED_USERS))
    }

    override fun onStop() {
        super.onStop()
        activity.unregisterReceiver(onReloadReceiver)
    }

    override fun onDetach() {
        if (isServiceBound) {
            statusManager.removeStatusListener(this)
        }
        super.onDetach()
    }

    protected fun executeLoader(requestMode: Int) {
        val sources = getFilterQueryAwait().sources.filterNotNull()

        when (requestMode) {
            TwitterListFragment.LOADER_LOAD_INIT -> {
                swipeRefreshLayout.isRefreshing = true
                sources.forEach { s ->
                    val userRecord = s.sourceAccount ?: return@forEach
                    val restQuery = s.getRestQuery() ?: return@forEach
                    loadingTaskKeys += statusManager.requestRestQuery(restTag,
                            userRecord, restQuery,
                            -1, true, s.hashCode().toString())
                }
                if (loadingTaskKeys.isEmpty()) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
            TwitterListFragment.LOADER_LOAD_UPDATE -> {
                if (sources.isEmpty()) setRefreshComplete()
                else {
                    clearUnreadNotifier()
                    sources.forEach { s ->
                        val userRecord = s.sourceAccount ?: return@forEach
                        val restQuery = s.getRestQuery() ?: return@forEach
                        loadingTaskKeys += statusManager.requestRestQuery(restTag,
                                userRecord, restQuery,
                                -1, true, s.hashCode().toString())
                    }
                    if (loadingTaskKeys.isEmpty()) {
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
        }
    }

    @Deprecated("フィルタタブにおいて従来のローダーAPIは非推奨です。")
    override fun executeLoader(requestMode: Int, userRecord: AuthUserRecord?) {
        Log.w(javaClass.simpleName, "フィルタタブにおいて従来のローダーAPIは非推奨です。")
        executeLoader(requestMode)
    }

    protected fun getFilterQueryAwait(): FilterQuery {
        //クエリのコンパイル待ち
        while (filterQuery == null) {
            Thread.sleep(100)
        }
        return filterQuery!!
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        //ユーザ情報を取得
        users = twitterService.users

        //クエリのコンパイルを開始
        try {
            filterQuery = QueryCompiler.compile(users, filterRawQuery)
        } catch (e: FilterCompilerException) {
            handler.post {
                elements.add(PreformedStatus(ExceptionStatus(Long.MAX_VALUE,
                        Exception("クエリのコンパイル中にエラーが発生しました。")), users.first()))
                elements.add(PreformedStatus(ExceptionStatus(Long.MAX_VALUE - 1, e), users.first()))
                notifyDataSetChanged()
            }
            filterQuery = QueryCompiler.compile(users, "from * where (false)")
        }

        //ストリーミングのリスナ登録
        statusManager.addStatusListener(this)

        //初期受信を実行
        if (elements.isEmpty()) {
            executeLoader(LOADER_LOAD_INIT)
        }
    }

    override fun onServiceDisconnected() {}

    override fun isCloseable(): Boolean = false

    override fun onRefresh() {
        executeLoader(TwitterListFragment.LOADER_LOAD_UPDATE)
    }

    override fun setRefreshComplete() {
        swipeRefreshLayout?.isRefreshing = false
    }

    override fun onListItemClick(position: Int, clickedElement: PreformedStatus?): Boolean {
        if (clickedElement != null && clickedElement.baseStatus is LoadMarkerStatus) {
            val loadMarkerStatus = clickedElement.baseStatus as LoadMarkerStatus
            if (loadMarkerStatus.taskKey < 0) {
                getFilterQueryAwait().sources.firstOrNull { it.hashCode().toString() == loadMarkerStatus.tag }?.let {
                    // リクエストの発行
                    val userRecord = it.sourceAccount ?: return@let
                    val restQuery = it.getRestQuery() ?: return@let
                    val taskKey = statusManager.requestRestQuery(restTag,
                            userRecord, restQuery,
                            loadMarkerStatus.id, true, it.hashCode().toString())
                    loadMarkerStatus.taskKey = taskKey
                    loadingTaskKeys += taskKey
                    queryingLoadMarkers.put(taskKey, loadMarkerStatus.id)
                    addLimitCount(100)
                    // Viewの表示更新
                    val visiblePosition = position - listView.firstVisiblePosition
                    if (visiblePosition > -1) {
                        val view: View? = listView.getChildAt(visiblePosition)
                        (view?.findViewById(R.id.pbLoading) as? ProgressBar)?.visibility = View.VISIBLE
                        (view?.findViewById(R.id.tvLoading) as? TextView)?.text = "loading"
                    }
                    // Debug Log
//                    putDebugLog("onListItemClick : Current firstVisiblePosition = ${listView.firstVisiblePosition}")
                }
            }
            return false
        } else if (clickedElement?.baseStatus !is FakeStatus) {
            return super.onListItemClick(position, clickedElement)
        } else {
            return false
        }
    }

    override fun getStreamFilter(): String? = null

    override fun getRestTag(): String = filterRawQuery

    override fun onStatus(from: AuthUserRecord, status: PreformedStatus, muted: Boolean) {
        if (elements.contains(status)) return
        if (status.baseStatusClass != LoadMarkerStatus::class.java && !getFilterQueryAwait().evaluate(status, users)) return

        when {
            muted -> {
                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("FilterListFragment", "[$filterRawQuery] onStatus : Muted ... $status")
                stash.add(status)
            }
            else -> {
                val useScrollLock = defaultSharedPreferences.getBoolean("pref_lock_scroll_after_reload", false)
                handler.post { insertElement2(status, useScrollLock && status is MetaStatus && "RestStream" == status.metadata) }
                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("[$filterRawQuery] onStatus : Insert  ... $status")
            }
        }
    }

    override fun onDirectMessage(from: AuthUserRecord, directMessage: DirectMessage) {}

    override fun onUpdatedStatus(from: AuthUserRecord, kind: Int, status: Status) {
        super.onUpdatedStatus(from, kind, status)
        when (kind) {
            StatusManager.UPDATE_REST_COMPLETED -> {
                status as RestCompletedStatus
                if (status.tag.equals(restTag)) {
                    loadingTaskKeys.remove(status.taskKey)
                    if (queryingLoadMarkers.indexOfKey(status.taskKey) > -1) {
                        elements.firstOrNull { it.baseStatus is LoadMarkerStatus && (it.baseStatus as LoadMarkerStatus).taskKey == status.taskKey }?.let {
                            handler.post { deleteElement(it) }
                        }
                        queryingLoadMarkers.remove(status.taskKey)
                    }
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("onUpdatedStatus : Rest Completed ... taskKey=${status.taskKey} , left loadingTaskKeys.size=${loadingTaskKeys.size}")
                    if (loadingTaskKeys.isEmpty()) {
                        handler.post { setRefreshComplete() }
                    }
                }
            }
        }
    }
}