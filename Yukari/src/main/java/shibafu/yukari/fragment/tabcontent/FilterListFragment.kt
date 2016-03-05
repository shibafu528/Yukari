package shibafu.yukari.fragment.tabcontent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.compiler.FilterCompilerException
import shibafu.yukari.filter.compiler.QueryCompiler
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.ExceptionStatus
import shibafu.yukari.twitter.statusimpl.FakeStatus
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusimpl.RestCompletedStatus
import shibafu.yukari.twitter.statusmanager.StatusListener
import shibafu.yukari.twitter.statusmanager.StatusManager
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
        val filterQuery = getFilterQuery()
        val queries = filterQuery.sources.filterNotNull().map { s ->
            val loader = s.getRestQuery() ?: return
            val user = s.sourceAccount ?: return
            Pair(user, loader)
        }
        when (requestMode) {
            TwitterListFragment.LOADER_LOAD_INIT -> {
                swipeRefreshLayout.isRefreshing = true
                queries.forEach { s ->
                    loadingTaskKeys += statusManager.requestRestQuery(restTag, s.first, true, s.second)
                }
            }
            TwitterListFragment.LOADER_LOAD_UPDATE -> {
                if (queries.isEmpty()) setRefreshComplete()
                else {
                    clearUnreadNotifier()
                    queries.forEach { s ->
                        loadingTaskKeys += statusManager.requestRestQuery(restTag, s.first, true, s.second)
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

    protected fun getFilterQuery(): FilterQuery {
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
    }

    override fun onServiceDisconnected() {}

    override fun isCloseable(): Boolean = false

    override fun onRefresh() {
        executeLoader(TwitterListFragment.LOADER_LOAD_UPDATE)
    }

    override fun setRefreshComplete() {
        swipeRefreshLayout.isRefreshing = false
    }

    override fun onListItemClick(clickedElement: PreformedStatus?): Boolean {
        if (clickedElement == null || !FakeStatus::class.java.isAssignableFrom(clickedElement.baseStatusClass)) {
            return super.onListItemClick(clickedElement)
        } else {
            return false
        }
    }

    override fun getStreamFilter(): String? = null

    override fun getRestTag(): String = filterRawQuery

    override fun onStatus(from: AuthUserRecord, status: PreformedStatus, muted: Boolean) {
        if (elements.contains(status)) return
        if (status.baseStatusClass != LoadMarkerStatus::class.java && !getFilterQuery().evaluate(status, users)) return

        when {
            muted -> {
                Log.d("FilterListFragment", "[$filterRawQuery] onStatus : Muted ... $status")
                stash.add(status)
            }
            else -> {
                insertElement2(status)
                putDebugLog("[$filterRawQuery] onStatus : Insert  ... $status")
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
                    putDebugLog("onUpdatedStatus : Rest Completed ... taskKey=${status.taskKey} , left loadingTaskKeys.size=${loadingTaskKeys.size}")
                    if (loadingTaskKeys.isEmpty()) {
                        handler.post { setRefreshComplete() }
                    }
                }
            }
        }
    }
}