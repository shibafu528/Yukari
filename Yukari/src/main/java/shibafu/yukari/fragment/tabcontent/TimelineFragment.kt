package shibafu.yukari.fragment.tabcontent

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.annotation.UiThread
import android.support.annotation.WorkerThread
import android.support.v4.util.LongSparseArray
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet
import shibafu.yukari.R
import shibafu.yukari.activity.MainActivity
import shibafu.yukari.activity.PreviewActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.activity.StatusActivity
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.common.TabType
import shibafu.yukari.common.TweetAdapter
import shibafu.yukari.common.async.ThrowableAsyncTask
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask
import shibafu.yukari.entity.ExceptionStatus
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Mention
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.compiler.FilterCompilerException
import shibafu.yukari.filter.compiler.QueryCompiler
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.fragment.SimpleListDialogFragment
import shibafu.yukari.fragment.base.ListYukariBaseFragment
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.TimelineEvent
import shibafu.yukari.linkage.TimelineObserver
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.media2.Media
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.putDebugLog
import shibafu.yukari.util.putWarnLog
import twitter4j.DirectMessage
import twitter4j.TwitterException

/**
 * 時系列順に要素を並べて表示するタブの基底クラス
 */
open class TimelineFragment : ListYukariBaseFragment(),
        TimelineTab,
        TimelineObserver,
        QueryableTab,
        AdapterView.OnItemLongClickListener,
        SwipeRefreshLayout.OnRefreshListener,
        SimpleListDialogFragment.OnDialogChoseListener,
        SimpleAlertDialogFragment.OnDialogChoseListener {
    var title: String = ""
    var mode: Int = 0
    var rawQuery: String = FilterQuery.VOID_QUERY_STRING
    var query: FilterQuery = FilterQuery.VOID_QUERY

    override val timelineId: String
        get() {
            val args = arguments
            if (args != null && args.containsKey(TwitterListFragment.EXTRA_ID)) {
                return args.getLong(TwitterListFragment.EXTRA_ID).toString()
            } else {
                return this.toString()
            }
        }

    protected val statuses: MutableList<Status> = arrayListOf()
    protected val mutedStatuses: MutableList<Status> = arrayListOf()
    protected val users: MutableList<AuthUserRecord> = arrayListOf()

    protected var statusAdapter: TweetAdapter? = null

    // タイムライン容量制限
    private var statusCapacity: Int = 256

    // SwipeRefreshLayout
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val onScrollListeners: MutableList<AbsListView.OnScrollListener> = arrayListOf()

    // リクエスト管理
    private val loadingTaskKeys = arrayListOf<Long>()
    private val queryingLoadMarkers = LongSparseArray<Long>() // TaskKey, LoadMarker.Id

    // ダブルクリック抑止
    private var blockingDoubleClick = false
    private var enableDoubleClickBlocker = false

    // 未読管理
    private val unreadSet = LongHashSet()
    private val unreadNotifierBehavior = UnreadNotifierBehavior(this, statuses, unreadSet)

    // スクロールロック
    private var lockedTarget: Status? = null
    private var lockedYPosition = 0
    private val scrollUnlockHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            lockedTarget = null
        }
    }

    // ListView Xタッチ座標 (画面幅に対する割合)
    private var listViewXTouchPercent: Float = 0f

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is MainActivity) {
            statuses.addAll(context.getStatusesList(timelineId))
            context.registerTwitterFragment(arguments!!.getLong(TweetListFragment.EXTRA_ID), this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle()
        title = arguments.getString(TwitterListFragment.EXTRA_TITLE) ?: ""
        mode = arguments.getInt(TwitterListFragment.EXTRA_MODE, -1)
        rawQuery = arguments.getString(EXTRA_FILTER_QUERY) ?: FilterQuery.VOID_QUERY_STRING
        arguments.getSerializable(TwitterListFragment.EXTRA_USER)?.let { users += it as AuthUserRecord }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        when (mode) {
            TabType.TABTYPE_TRACE, TabType.TABTYPE_DM ->
                return super.onCreateView(inflater, container, savedInstanceState)
        }
        val v = inflater.inflate(R.layout.fragment_swipelist, container, false)

        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout) as SwipeRefreshLayout?
        swipeRefreshLayout?.setColorSchemeResources(AttrUtil.resolveAttribute(requireContext().theme, R.attr.colorPrimary))
        swipeRefreshLayout?.setOnRefreshListener(this)

        val swipeActionStatusView = v.findViewById<View>(R.id.swipeActionStatusFrame)
        swipeActionStatusView?.visibility = View.INVISIBLE

        unreadNotifierBehavior.onCreateView(v)

        return v
    }

    override fun onStart() {
        super.onStart()
        unreadNotifierBehavior.onStart()
    }

    override fun onResume() {
        super.onResume()

        val activity = requireActivity()
        if (activity !is MainActivity) {
            if (activity is AppCompatActivity) {
                activity.supportActionBar?.title = title
            } else {
                activity.title = title
            }
        }

        enableDoubleClickBlocker = defaultSharedPreferences.getBoolean("pref_block_doubleclock", false)
        blockingDoubleClick = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusAdapter = TweetAdapter(context, users, null, statuses)
        listAdapter = statusAdapter

        listView.setOnTouchListener { v, event ->
            listViewXTouchPercent = event.x * 100 / v.width
            false
        }
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                onScrollListeners.forEach { it.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount) }
            }

            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                onScrollListeners.forEach { it.onScrollStateChanged(view, scrollState) }
            }
        })
        listView.setOnItemLongClickListener(this)
        onScrollListeners.add(unreadNotifierBehavior)
        onScrollListeners.add(object : AbsListView.OnScrollListener {
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("onScroll called, lockedScrollTimestamp = ${lockedTarget?.createdAt?.time ?: -1}, y = $lockedYPosition")
            }

            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    lockedTarget = null
                }
            }
        })
        unreadNotifierBehavior.onViewCreated(view, savedInstanceState)
    }

    override fun onStop() {
        super.onStop()
        unreadNotifierBehavior.onStop()
    }

    override fun onDetach() {
        super.onDetach()
        onScrollListeners.remove(unreadNotifierBehavior)
        unreadNotifierBehavior.onDetach()
        if (isTwitterServiceBound) {
            twitterService?.timelineHub?.removeObserver(this)
        }
        listAdapter = null
        statusAdapter = null
        if (activity is MainActivity) {
            val statusesList = (activity as MainActivity).getStatusesList(timelineId)
            statusesList.clear()
            statusesList.addAll(statuses)
            (activity as MainActivity).unregisterTwitterFragment(arguments!!.getLong(TweetListFragment.EXTRA_ID))
        }
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        if (blockingDoubleClick) {
            return
        }

        if (position < statuses.size) {
            val result =
                    when (val clickedElement = statuses[position]) {
                        is LoadMarker -> {
                            if (clickedElement.taskKey < 0) {
                                query.sources.firstOrNull { it.hashCode().toString() == clickedElement.loadMarkerTag }?.let {
                                    // リクエストの発行
                                    val userRecord = it.sourceAccount ?: return@let
                                    val restQuery = it.getRestQuery() ?: return@let
                                    val params = RestQuery.Params(maxId = clickedElement.id,
                                            loadMarkerTag = it.hashCode().toString(),
                                            loadMarkerDate = clickedElement.createdAt,
                                            stringCursor = clickedElement.stringCursor,
                                            longCursor = clickedElement.longCursor)
                                    val taskKey = twitterService.statusLoader.requestRestQuery(timelineId,
                                            userRecord, restQuery, params)
                                    clickedElement.taskKey = taskKey
                                    loadingTaskKeys += taskKey
                                    queryingLoadMarkers.put(taskKey, clickedElement.id)
                                    statusCapacity += 100
                                    // Viewの表示更新
                                    val visiblePosition = position - listView.firstVisiblePosition
                                    if (visiblePosition > -1) {
                                        val view: View? = listView.getChildAt(visiblePosition)
                                        view?.findViewById<View>(R.id.pbLoading)?.visibility = View.VISIBLE
                                        view?.findViewById<TextView>(R.id.tvLoading)?.text = "loading"
                                    }
                                }
                            }
                            false // ダブルクリックブロックの対象外
                        }
                        is TwitterStatus, is DonStatus -> {
                            val action = when {
                                listViewXTouchPercent <= 25f -> defaultSharedPreferences.getString("pref_timeline_click_action_left", "open_detail")
                                listViewXTouchPercent >= 75f -> defaultSharedPreferences.getString("pref_timeline_click_action_right", "open_detail")
                                else -> defaultSharedPreferences.getString("pref_timeline_click_action_center", "open_detail")
                            }

                            onGeneralItemClick(position, clickedElement, action.orEmpty())
                        }
                        is TwitterMessage -> {
                            val links = if (clickedElement.user.id != clickedElement.mentions.first().id) {
                                clickedElement.mentions.map { "@" + it.screenName } +
                                        clickedElement.media.map { it.browseUrl } +
                                        clickedElement.links +
                                        clickedElement.tags
                            } else {
                                clickedElement.media.map { it.browseUrl } +
                                        clickedElement.links +
                                        clickedElement.tags
                            }
                            val bundle = Bundle()
                            bundle.putSerializable(EXTRA_STATUS, clickedElement)
                            val items = listOf("返信する", "削除する", "@${clickedElement.user.screenName}") + links
                            val dialog = SimpleListDialogFragment.newInstance(DIALOG_REQUEST_TWITTER_MESSAGE_MENU,
                                    "@${clickedElement.user.screenName}:${clickedElement.text}",
                                    null, null, null,
                                    items, bundle)
                            dialog.setTargetFragment(this, 0)
                            dialog.show(fragmentManager, "twitter_message_menu")
                            true
                        }
                        is NotifyHistory -> {
                            val bundle = Bundle()
                            bundle.putSerializable(EXTRA_STATUS, clickedElement)
                            val dialog = SimpleListDialogFragment.newInstance(DIALOG_REQUEST_HISTORY_MENU,
                                    "メニュー", null, null, null,
                                    listOf("@${clickedElement.user.screenName}", "詳細を開く"),
                                    bundle)
                            dialog.setTargetFragment(this, 0)
                            dialog.show(fragmentManager, "history_menu")
                            true
                        }
                        else -> false
                    }
            // コマンド実行成功後、次回onResumeまでクリックイベントを無視する
            if (result && enableDoubleClickBlocker) {
                blockingDoubleClick = true
            }
        }
    }

    override fun onItemLongClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Boolean {
        if (blockingDoubleClick) {
            return false
        }

        if (position < statuses.size) {
            val result =
                    when (val clickedElement = statuses[position]) {
                        is TwitterStatus, is DonStatus -> {
                            val action = when {
                                listViewXTouchPercent <= 25f -> defaultSharedPreferences.getString("pref_timeline_long_click_action_left", "")
                                listViewXTouchPercent >= 75f -> defaultSharedPreferences.getString("pref_timeline_long_click_action_right", "")
                                else -> defaultSharedPreferences.getString("pref_timeline_long_click_action_center", "")
                            }

                            onGeneralItemClick(position, clickedElement, action.orEmpty())
                        }
                        else -> false
                    }

            // コマンド実行成功後、次回onResumeまでクリックイベントを無視する
            if (result && enableDoubleClickBlocker) {
                blockingDoubleClick = true
            }

            return true
        }

        return false
    }

    override fun scrollToTop() {
        try {
            listView?.setSelection(0)
            unreadNotifierBehavior.clearUnreadNotifier()
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
        try {
            if (unreadSet.isEmpty) {
                listView.setSelection(0)
            } else {
                val lastUnreadId = unreadSet.min()
                for (position in 0 until statuses.size) {
                    if (statuses[position].id == lastUnreadId) {
                        listView.setSelection(position)
                        break
                    }
                }
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
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
            statusAdapter?.setStatusLoader(twitterService.statusLoader)
        }

        // クエリコンパイル
        try {
            query = QueryCompiler.compile(users, rawQuery)
        } catch (e: FilterCompilerException) {
            handler.post {
                statuses.add(ExceptionStatus(Long.MAX_VALUE, users.first(),
                        Exception("クエリのコンパイル中にエラーが発生しました。")))
                statuses.add(ExceptionStatus(Long.MAX_VALUE - 1, users.first(), e))
                notifyDataSetChanged()
            }
            query = FilterQuery.VOID_QUERY
        }
        // コンパイル完了をMainActivityに通知
        activity.let { activity ->
            if (activity is MainActivity) {
                activity.onQueryCompiled(this, query)
            }
        }

        // イベント購読開始
        twitterService?.timelineHub?.addObserver(this)

        // 初期読み込み
        if (statuses.isEmpty()) {
            val statusLoader = twitterService?.statusLoader ?: return
            query.sources.forEach { source ->
                val userRecord = source.sourceAccount ?: return@forEach
                val restQuery = source.getRestQuery() ?: return@forEach
                val params = RestQuery.Params(loadMarkerTag = source.hashCode().toString())
                loadingTaskKeys += statusLoader.requestRestQuery(timelineId, userRecord, restQuery, params)
            }
            if (loadingTaskKeys.isEmpty()) {
                swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    override fun onServiceDisconnected() {}

    override fun onRefresh() {
        if (query.sources.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
            return
        }

        swipeRefreshLayout?.isRefreshing = true
        unreadNotifierBehavior.clearUnreadNotifier()
        val statusLoader = twitterService?.statusLoader ?: return
        query.sources.forEach { source ->
            val userRecord = source.sourceAccount ?: return@forEach
            val restQuery = source.getRestQuery() ?: return@forEach
            val params = RestQuery.Params(loadMarkerTag = source.hashCode().toString())
            loadingTaskKeys += statusLoader.requestRestQuery(timelineId, userRecord, restQuery, params)
        }
        if (loadingTaskKeys.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, value: String?, extras: Bundle?) {
        when (requestCode) {
            DIALOG_REQUEST_HISTORY_MENU -> {
                blockingDoubleClick = false
                if (extras == null) return
                val status = extras.getSerializable(EXTRA_STATUS) as NotifyHistory

                when (which) {
                    // プロフィール
                    0 -> {
                        val intent = ProfileActivity.newIntent(requireActivity().applicationContext, status.representUser, Uri.parse(status.user.url))
                        startActivity(intent)
                    }
                    // 詳細を開く
                    1 -> {
                        val intent = Intent(requireActivity().applicationContext, StatusActivity::class.java)
                        intent.putExtra(StatusActivity.EXTRA_USER, status.representUser)
                        intent.putExtra(StatusActivity.EXTRA_STATUS, status.status)
                        startActivity(intent)
                    }
                }
            }
            DIALOG_REQUEST_TWITTER_MESSAGE_MENU -> {
                blockingDoubleClick = false
                if (extras == null || value == null) return
                val status = extras.getSerializable(EXTRA_STATUS) as TwitterMessage

                when (which) {
                    // 返信する
                    0 -> {
                        val intent = Intent(activity, TweetActivity::class.java)
                        intent.putExtra(TweetActivity.EXTRA_USER, status.representUser)
                        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM)
                        intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, TwitterUtil.getUrlFromUserId(status.user.id))
                        intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, status.user.screenName)
                        startActivity(intent)
                    }
                    // 削除する
                    1 -> {
                        val dialog = SimpleAlertDialogFragment.Builder(DIALOG_REQUEST_TWITTER_MESSAGE_DELETE)
                                .setTitle("確認")
                                .setMessage("メッセージを削除してもよろしいですか？")
                                .setPositive("OK")
                                .setNegative("キャンセル")
                                .setExtras(extras)
                                .build()
                        dialog.setTargetFragment(this, 0)
                        dialog.show(fragmentManager, "twitter_message_delete")
                    }
                    // 送信者
                    2 -> {
                        val intent = ProfileActivity.newIntent(requireActivity(), status.representUser, Uri.parse(status.user.url))
                        startActivity(intent)
                    }
                    // リンクとか
                    else -> {
                        val links = if (status.user.id != status.mentions.first().id) {
                            status.mentions + status.media + status.links + status.tags
                        } else {
                            status.media + status.links + status.tags
                        }
                        val chose = links[which - 3]
                        when (chose) {
                            is Mention -> {
                                val intent = ProfileActivity.newIntent(requireActivity(), status.representUser, Uri.parse(chose.url))
                                startActivity(intent)
                            }
                            is Media -> {
                                val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(chose.browseUrl),
                                        requireActivity().applicationContext,
                                        PreviewActivity::class.java)
                                intent.putExtra(PreviewActivity.EXTRA_USER, status.representUser)
                                startActivity(intent)
                            }
                            is String -> {
                                if (chose.startsWith("http://") || chose.startsWith("https://")) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(chose))
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    startActivity(intent)
                                } else {
                                    val intent = Intent(activity, MainActivity::class.java)
                                    intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, chose)
                                    startActivity(intent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_REQUEST_TWITTER_MESSAGE_DELETE -> {
                blockingDoubleClick = false
                if (extras == null) return
                val status = extras.getSerializable(EXTRA_STATUS) as TwitterMessage

                if (which == DialogInterface.BUTTON_POSITIVE) {
                    object : ThrowableTwitterAsyncTask<DirectMessage, Boolean>() {

                        override fun doInBackground(vararg params: DirectMessage): ThrowableAsyncTask.ThrowableResult<Boolean> {
                            var user: AuthUserRecord? = null
                            for (userRecord in twitterService.users) {
                                if (params[0].recipientId == userRecord.NumericId || params[0].senderId == userRecord.NumericId) {
                                    user = userRecord
                                }
                            }
                            if (user == null) {
                                return ThrowableAsyncTask.ThrowableResult(IllegalArgumentException("操作対象のユーザが見つかりません."))
                            }
                            try {
                                val t = twitterService.getTwitterOrThrow(user)
                                t.destroyDirectMessage(status.id)
                            } catch (e: TwitterException) {
                                e.printStackTrace()
                                return ThrowableAsyncTask.ThrowableResult(e)
                            }

                            return ThrowableAsyncTask.ThrowableResult(true)
                        }

                        override fun onPostExecute(result: ThrowableAsyncTask.ThrowableResult<Boolean>) {
                            super.onPostExecute(result)
                            if (!isErrored && result.result) {
                                showToast("削除しました")
                            }
                        }

                        override fun showToast(message: String) {
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                        }
                    }.execute(status.message)
                }
            }
        }

        // 要素クリックイベントでダイアログを使用した場合のコールバック処理
        ITEM_CLICK_ACTIONS.forEach { (_, instance) ->
            instance.onDialogChose().invoke(this, requestCode, which, extras)
        }
    }

    override fun getQueryableElements(): MutableCollection<Status> = ArrayList(statuses)

    @WorkerThread
    override fun onTimelineEvent(event: TimelineEvent) {
        if (isDetached || activity == null) {
            // デタッチ状態か親Activityが無い場合はだいたい何もできないので捨てる
            putWarnLog("[EVENT DROPPED!] Fragment is detached or parent actiivty is null.")
            return
        }

        when (event) {
            is TimelineEvent.Received -> {
                val status = event.status
                val queryVariables = mapOf<String, Any?>(
                        "passive" to event.passive,
                        "timelineId" to event.timelineId
                )

                if (statuses.contains(status)) return
                if (status !is LoadMarker && !query.evaluate(status, users, queryVariables)) return

                if (event.muted) {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("TimelineFragment", "[$rawQuery] onStatus : Muted ... $status")

                    mutedStatuses += status
                } else {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("[$rawQuery] onStatus : Insert  ... $status")

                    val useScrollLock = defaultSharedPreferences.getBoolean("pref_lock_scroll_after_reload", false)
                    handler.post { insertElement(status, !event.passive && useScrollLock && status !is LoadMarker) }
                }
            }
            is TimelineEvent.RestRequestCompleted -> {
                if (event.timelineId == timelineId) {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("onUpdatedStatus : Rest Completed ... taskKey=${event.taskKey} , left loadingTaskKeys.size=${loadingTaskKeys.size}")

                    handler.post {
                        finishRestRequest(event.taskKey)
                    }
                }
            }
            is TimelineEvent.RestRequestCancelled -> {
                if (event.timelineId == timelineId) {
                    handler.post {
                        finishRestRequest(event.taskKey)
                    }
                }
            }
            is TimelineEvent.Notify -> {
                if (mode == TabType.TABTYPE_HISTORY) {
                    handler.post { insertElement(event.notify, false) }
                }
            }
            is TimelineEvent.Favorite -> {
                handler.post {
                    setFavoriteState(event.from, event.status, true)
                }
            }
            is TimelineEvent.Unfavorite -> {
                handler.post {
                    setFavoriteState(event.from, event.status, false)
                }
            }
            is TimelineEvent.Delete -> {
                handler.post { deleteElement(event.providerHost, event.id) }
                mutedStatuses.removeAll { it.providerHost == event.providerHost && it.id == event.id }
            }
            is TimelineEvent.Wipe -> {
                handler.post {
                    statuses.clear()
                    notifyDataSetChanged()
                }
                mutedStatuses.clear()
            }
            is TimelineEvent.ForceUpdateUI -> {
                handler.post {
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun onGeneralItemClick(position: Int, clickedElement: Status, action: String): Boolean {
        return ITEM_CLICK_ACTIONS[action]?.onItemClick()?.invoke(this, clickedElement) ?: false
    }

    /**
     * TL要素挿入の前処理として、挿入位置の判定とマージを実施します。
     * @param status Status
     * @return 挿入位置、負数の場合は実際にリストに挿入する必要はないが別の処理が必要。(PRE_INSERT_から始まる定数を参照)
     */
    private fun preInsertElement(status: Status): Int {
        // 代表操作アカウントの書き換えが必要か確認する
        // 自身の所有するStatusの場合、書き換えてはいけない
        if (!status.isOwnedStatus()) {
            // 優先アカウント設定が存在するか？
            val userExtras = twitterService.userExtras.firstOrNull { it.id == status.originStatus.user.identicalUrl }
            if (userExtras != null && userExtras.priorityAccount != null) {
                status.representUser = userExtras.priorityAccount
                if (!status.receivedUsers.contains(userExtras.priorityAccount)) {
                    status.receivedUsers.add(userExtras.priorityAccount)
                }
            }
        }

        if (status is LoadMarker) {
            for (i in 0 until statuses.size) {
                if (statuses[i] is LoadMarker) {
                    val compareTo = statuses[i] as LoadMarker

                    if (status.providerApiType == compareTo.providerApiType && status.anchorStatusId == compareTo.anchorStatusId && status.user.id == compareTo.user.id) {
                        // 同じ情報を持つLoadMarkerなので、挿入しない
                        return PRE_INSERT_DUPLICATED
                    }
                } else if (status > statuses[i]) {
                    return i
                }
            }
        } else {
            for (i in 0 until statuses.size) {
                if (status == statuses[i]) {
                    if (status.providerApiType == statuses[i].providerApiType) {
                        statuses[i] = statuses[i].merge(status)
                        return PRE_INSERT_MERGED
                    } else if (status.providerApiType < statuses[i].providerApiType) {
                        return i
                    }
                } else if (status > statuses[i]) {
                    return i
                }
            }
        }
        return statuses.size
    }

    /**
     * TLに要素を挿入します。既に存在する場合はマージします。
     * @param status Status
     * @param useScrollLock スクロールロックを使うかどうか
     */
    private fun insertElement(status: Status, useScrollLock: Boolean) {
        val position = preInsertElement(status)
        when (position) {
            PRE_INSERT_DUPLICATED -> return
            PRE_INSERT_MERGED -> {
                notifyDataSetChanged()
                return
            }
            else -> if (!statuses.contains(status)) {
                if (position < statuses.size && statuses[position].id == status.id) {
                    return
                }
                statuses.add(position, status)
                notifyDataSetChanged()
                if (statusCapacity < statuses.size) {
                    val iterator = statuses.listIterator(statusCapacity)
                    while (iterator.hasNext()) {
                        val s = iterator.next()
                        if (s !is LoadMarker) {
                            unreadSet.remove(s.id)
                            iterator.remove()
                            notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        val listView = try {
            listView
        } catch (ignored: IllegalStateException) {
            putWarnLog("Insert: ListView is null. DROPPED! ($status, $position)")
            return
        }
        
        // ここからスクロール制御回りの処理

        val firstPos = listView.firstVisiblePosition
        val firstView = listView.getChildAt(0)
        val y = firstView?.top ?: 0
        val locked = lockedTarget
        if (!useScrollLock && (statuses.size == 1 || firstPos == 0 && y > -1)) {
            listView.setSelection(0)

            if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = 0 (Top) ... $status")
        } else if (locked != null) {
            for (i in statuses.indices) {
                // 同一の投稿が見つからなければ、記憶されているロック対象のタイムスタンプより古い投稿を代わりとする。
                if (statuses[i] == locked || statuses[i].createdAt.time < locked.createdAt.time) {
                    listView.setSelectionFromTop(i, y)
                    if (position < i && status !is LoadMarker) {
                        unreadSet.add(status.id)
                    }

                    scrollUnlockHandler.removeCallbacksAndMessages(null)
                    scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, i, y), 200)

                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = $i, ${locked.createdAt.time}, ${locked.url} (Locked strict) ... $status")
                    break
                }
            }
        } else if (position <= firstPos) {
            if (statuses.size <= 1 && status is LoadMarker) {
                // 要素数1の時にマーカーを掴むとずっと下にスクロールされてしまうので回避する
            } else {
                var lockedPosition = firstPos + 1
                if (lockedPosition < statuses.size) {
                    if (statuses[lockedPosition] is LoadMarker) {
                        lockedPosition = firstPos
                    }
                } else {
                    lockedPosition = statuses.size - 1
                }

                if (status !is LoadMarker) {
                    unreadSet.add(status.id)
                }
                listView.setSelectionFromTop(lockedPosition, y)

                lockedTarget = statuses[lockedPosition]
                lockedYPosition = y

                scrollUnlockHandler.removeCallbacksAndMessages(null)
                scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, firstPos + 1, y), 200)

                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = ${statuses[lockedPosition].createdAt.time}, ${statuses[lockedPosition].url} (Locked) ... $status")
            }
        } else {
            if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = $firstPos (Not changed) ... $status")
        }

        unreadNotifierBehavior.updateUnreadNotifier()
    }

    /**
     * TLから要素を削除します。
     * @param providerHost 削除対象の [Provider] ホスト名
     * @param id 削除対象のID
     */
    private fun deleteElement(providerHost: String, id: Long) {
        val listView = try {
            listView
        } catch (e: IllegalStateException) {
            putWarnLog("Delete: ListView is null. DROPPED! ($providerHost, $id)")
            return
        }

        val iterator = statuses.listIterator()
        while (iterator.hasNext()) {
            val index = iterator.nextIndex()
            val status = iterator.next()
            if (status.providerHost == providerHost && status.id == id) {
                iterator.remove()
                notifyDataSetChanged()
                if (unreadSet.contains(id)) {
                    unreadSet.remove(id)
                    unreadNotifierBehavior.updateUnreadNotifier()
                }

                val firstPos = listView.firstVisiblePosition
                val firstView = listView.getChildAt(0)
                val y = firstView?.top ?: 0
                val locked = lockedTarget

                if (statuses.size == 1 || firstPos == 0) {
                    listView.setSelection(0)
                } else if (locked != null) {
                    for (i in statuses.indices) {
                        if (statuses[i] == locked || statuses[i].createdAt.time < locked.createdAt.time) {
                            listView.setSelectionFromTop(i, y)
                            break
                        }
                    }
                } else {
                    if (index < firstPos) {
                        listView.setSelectionFromTop(firstPos - 1, y)
                    } else {
                        listView.setSelectionFromTop(firstPos, y)
                    }
                }

                break
            }
        }
    }

    /**
     * RESTリクエストのロードマーカーを削除し、リクエスト中状態を解除します。
     * @param taskKey 非同期処理キー
     */
    @UiThread
    private fun finishRestRequest(taskKey: Long) {
        loadingTaskKeys.remove(taskKey)
        if (queryingLoadMarkers.indexOfKey(taskKey) > -1) {
            statuses.firstOrNull { it is LoadMarker && it.taskKey == taskKey }?.let {
                deleteElement(it.providerHost, it.id)
            }
            queryingLoadMarkers.remove(taskKey)
        }
        if (loadingTaskKeys.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    /**
     * TL上の要素のお気に入り状態を更新します。
     * @param eventFrom お気に入り登録・解除を実行したユーザ
     * @param eventStatus 対象の [Status]
     * @param isFavorited お気に入り状態
     */
    @UiThread
    private fun setFavoriteState(eventFrom: User, eventStatus: Status, isFavorited: Boolean) {
        statuses.forEach { status ->
            if (status.javaClass == eventStatus.javaClass && status.id == eventStatus.id) {
                status.metadata.favoritedUsers.put(eventFrom.id, isFavorited)
                if (status.user.id == eventStatus.representUser.NumericId && !status.receivedUsers.contains(eventStatus.representUser)) {
                    status.receivedUsers.add(eventStatus.representUser)
                }
                notifyDataSetChanged()
                return
            }
        }
        mutedStatuses.forEach { status ->
            if (status.javaClass == eventStatus.javaClass && status.id == eventStatus.id) {
                status.metadata.favoritedUsers.put(eventFrom.id, isFavorited)
                if (status.user.id == eventStatus.representUser.NumericId && !status.receivedUsers.contains(eventStatus.representUser)) {
                    status.receivedUsers.add(eventStatus.representUser)
                }
                return
            }
        }
    }

    private fun notifyDataSetChanged() {
        statusAdapter?.notifyDataSetChanged()
    }

    companion object {
        const val EXTRA_FILTER_QUERY = "filterQuery"

        /** TL容量の初期化係数 */
        private const val CAPACITY_INITIAL_FACTOR = 256

        /** [preInsertElement] : 既に同一の要素が存在している(挿入禁止) */
        private const val PRE_INSERT_DUPLICATED = -1
        /** [preInsertElement] : 同一要素があったためマージを行った(Viewの制御のみ必要) */
        private const val PRE_INSERT_MERGED = -2

        /** Extra/Bundle Key : Status */
        private const val EXTRA_STATUS = "status"

        /** ダイアログID : NotifyHistory クリックメニュー */
        private const val DIALOG_REQUEST_HISTORY_MENU = 1
        /** ダイアログID : TwitterMessage クリックメニュー */
        private const val DIALOG_REQUEST_TWITTER_MESSAGE_MENU = 2
        /** ダイアログID : TwitterMessage 削除確認 */
        private const val DIALOG_REQUEST_TWITTER_MESSAGE_DELETE = 3

        /** ダイアログID : Action Favorite 確認 */
        internal const val DIALOG_REQUEST_ACTION_FAVORITE = 10
        /** ダイアログID : Action Repost 確認 */
        internal const val DIALOG_REQUEST_ACTION_REPOST = 11
        /** ダイアログID : Action Fav&Repost 確認 */
        internal const val DIALOG_REQUEST_ACTION_FAV_AND_REPOST = 12

        private val ITEM_CLICK_ACTIONS = mapOf(
                "open_detail" to TimelineItemClickAction.OPEN_DETAIL,
                "open_profile" to TimelineItemClickAction.OPEN_PROFILE,
                "open_thread" to TimelineItemClickAction.OPEN_THREAD,
                "reply" to TimelineItemClickAction.REPLY,
                "reply_all" to TimelineItemClickAction.REPLY_ALL,
                "favorite" to TimelineItemClickAction.FAVORITE,
                "repost" to TimelineItemClickAction.REPOST,
                "fav_and_repost" to TimelineItemClickAction.FAV_AND_REPOST
        )
    }
}
