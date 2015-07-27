package shibafu.yukari.fragment.tabcontent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.compiler.QueryCompiler
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.rest.RESTParams
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusmanager.StatusListener
import shibafu.yukari.twitter.statusmanager.StatusManager
import twitter4j.DirectMessage
import twitter4j.Status

/**
 * Created by shibafu on 15/06/06.
 */
public class FilterListFragment : TweetListFragment(), StatusListener {
    companion object {
        val EXTRA_FILTER_QUERY = "filterQuery"
    }

    private var filterRawQuery = ""
    private var filterQuery: FilterQuery? = null

    private val onReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    override fun onAttach(activity: Activity?) {
        super<TweetListFragment>.onAttach(activity)
        filterRawQuery = getArguments()?.getString(EXTRA_FILTER_QUERY) ?: ""
    }

    override fun onStart() {
        super<TweetListFragment>.onStart()
        getActivity().registerReceiver(onReloadReceiver, IntentFilter(TwitterService.RELOADED_USERS))
    }

    override fun onStop() {
        super<TweetListFragment>.onStop()
        getActivity().unregisterReceiver(onReloadReceiver)
    }

    override fun onDetach() {
        if (isServiceBound()) {
            getStatusManager().removeStatusListener(this)
        }
        super<TweetListFragment>.onDetach()
    }

    protected fun executeLoader(requestMode: Int) {
        val filterQuery = getFilterQuery()
        val loaders = filterQuery.sources.mapNotNull { s ->
            val loader = s.getRESTLoader(getActivity(), null) ?: return
            val user = s.sourceAccount ?: return
            Pair(user, loader)
        }
        when (requestMode) {
            TwitterListFragment.LOADER_LOAD_INIT -> loaders.forEach { s -> s.second.execute(RESTParams(s.first)) }
            TwitterListFragment.LOADER_LOAD_UPDATE -> {
                if (loaders.isEmpty()) setRefreshComplete()
                else {
                    clearUnreadNotifier()
                    loaders.forEach { s -> s.second.execute(RESTParams(s.first, true)) }
                }
            }
        }
    }

    override fun executeLoader(requestMode: Int, userRecord: AuthUserRecord?) = executeLoader(requestMode)

    protected fun getFilterQuery(): FilterQuery {
        //クエリのコンパイル待ち
        while (filterQuery == null) {
            Thread.sleep(100)
        }
        return filterQuery!!
    }

    override fun onServiceConnected() {
        super<TweetListFragment>.onServiceConnected()
        //ユーザ情報を取得
        users = getTwitterService().getUsers()

        //クエリのコンパイルを開始
        filterQuery = QueryCompiler.compile(users, filterRawQuery)

        //ストリーミングのリスナ登録
        getStatusManager().addStatusListener(this)
    }

    override fun onServiceDisconnected() {}

    override fun isCloseable(): Boolean = false

    override fun getStreamFilter(): String? = null

    override fun onStatus(from: AuthUserRecord, status: PreformedStatus, muted: Boolean) {
        if (elements.contains(status) || !getFilterQuery().evaluate(status, users)) return

        when {
            muted -> stash.add(status)
            else -> {
                val position = prepareInsertStatus(status)
                if (position > -1) {
                    getHandler().post { insertElement(status, position) }
                }
            }
        }
    }

    override fun onDirectMessage(from: AuthUserRecord, directMessage: DirectMessage) {}

    override fun onUpdatedStatus(from: AuthUserRecord, kind: Int, status: Status) {
        when (kind) {
            StatusManager.UPDATE_WIPE_TWEETS -> {
                getHandler().post {
                    elements.clear()
                    notifyDataSetChanged()
                }
                stash.clear()
            }
            StatusManager.UPDATE_FORCE_UPDATE_UI -> getHandler().post {
                notifyDataSetChanged()
            }
            StatusManager.UPDATE_DELETED -> {
                getHandler().post { deleteElement(status) }
                stash.removeAll(stash.filter {s -> s.getId() == status.getId()})
            }
            StatusManager.UPDATE_FAVED, StatusManager.UPDATE_UNFAVED -> {
                val position = elements.indexOfFirst { s -> s.getId() == status.getId() }
                if (position > -1) {
                    getHandler().post {
                        elements.get(position).merge(status, from)
                        notifyDataSetChanged()
                    }
                } else {
                    stash.filter { s -> s.getId() == status.getId() }
                         .forEach { s -> s.merge(status, from) }
                }
            }
        }
    }
}