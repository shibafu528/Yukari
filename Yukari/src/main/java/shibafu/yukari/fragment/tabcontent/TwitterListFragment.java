package shibafu.yukari.fragment.tabcontent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.ColorRes;
import android.support.annotation.IntDef;
import android.support.annotation.UiThread;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetAdapter;
import shibafu.yukari.entity.Status;
import shibafu.yukari.linkage.TimelineEvent;
import shibafu.yukari.linkage.TimelineObserver;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.util.AttrUtil;
import twitter4j.TwitterResponse;
import twitter4j.auth.AccessToken;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TwitterListFragment<T extends TwitterResponse>
        extends     ListFragment
        implements  TwitterServiceConnection.ServiceConnectionCallback,
                    SwipeRefreshLayout.OnRefreshListener,
                    TwitterServiceDelegate,
                    TimelineTab,
                    TimelineObserver {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int LOADER_LOAD_INIT   = 0;
    public static final int LOADER_LOAD_MORE   = 1;
    public static final int LOADER_LOAD_UPDATE = 2;

    /** {@link PrepareInsertResultCode} : 同時に返すpositionに挿入可能 */
    protected static final int PREPARE_INSERT_ALLOWED = 0;
    /** {@link PrepareInsertResultCode} : 既に同一の要素が存在している(挿入禁止) */
    protected static final int PREPARE_INSERT_DUPLICATED = -1;
    /** {@link PrepareInsertResultCode} : 同一要素があったためマージを行った(Viewの制御のみ必要) */
    protected static final int PREPARE_INSERT_MERGED = -2;

    protected static final boolean USE_INSERT_LOG = false;

    //Elements List
    private Class<T> elementClass;
    protected ArrayList<T> elements = new ArrayList<>();
    protected ListView listView;
    protected TweetAdapter tweetAdapter;

    //Elements Limit
    private boolean limitedTimeline;
    private int limitCount = 256;

    //Unread Set
    private long lastShowedFirstItemId = -1;
    private int lastShowedFirstItemY = 0;
    private View unreadNotifierView;
    private TextView tvUnreadCount;
    private MutableLongSet unreadSet = new LongHashSet();

    //Binding Accounts
    protected List<AuthUserRecord> users = new ArrayList<AuthUserRecord>() {
        @Override
        public boolean add(AuthUserRecord object) {
            return !users.contains(object) && super.add(object);
        }

        @Override
        public boolean addAll(Collection<? extends AuthUserRecord> collection) {
            ArrayList<AuthUserRecord> dup = new ArrayList<>(collection);
            dup.removeAll(users);
            return super.addAll(collection);
        }
    };

    //Fragment States
    private String title;
    private int mode;

    //SwipeRefreshLayout
    private SwipeRefreshLayout swipeRefreshLayout;
    private int refreshCounter;
    private boolean disabledReload;
    @ColorRes private int swipeRefreshColor;

    //Footer View
    private View footerView;
    private View footerProgress;
    private TextView footerText;
    private boolean isLoading = false;

    //Twitter Wrapper
    private TwitterServiceConnection connection = new TwitterServiceConnection(this);

    //TweetCommon Delegate
    private TweetCommonDelegate commonDelegate;

    //DoubleClick Blocker
    private boolean enableDoubleClickBlocker;
    private boolean blockingDoubleClick;

    //Scroll Lock
    protected long lockedScrollId = -1;
    private int lockedYPosition = 0;
    private Handler scrollUnlockHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (USE_INSERT_LOG) {
                Log.d("scrollUnlockHandler", "(reset) lockedScrollId = " + lockedScrollId + ", y = " + lockedYPosition);
            }
            lockedScrollId = -1;
        }
    };

    private Handler handler = new Handler();

    public TwitterListFragment() {
        setRetainInstance(true);
    }

    public TwitterListFragment(Class<T> elementClass) {
        setRetainInstance(true);

        this.elementClass = elementClass;
        commonDelegate = TweetCommon.newInstance(elementClass);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            Bundle args = getArguments();
            long id = args.getLong(EXTRA_ID);
            elements = ((MainActivity) context).getElementsList(id);

            ((MainActivity) context).registerTwitterFragment(id, this);
        }
        swipeRefreshColor = AttrUtil.resolveAttribute(context.getTheme(), R.attr.colorPrimary);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        AuthUserRecord manager = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        if (manager != null) {
            users.add(manager);
        }
        mode = args.getInt(EXTRA_MODE, -1);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        switch (getMode()) {
            case TabType.TABTYPE_TRACE:
            case TabType.TABTYPE_DM:
                return super.onCreateView(inflater, container, savedInstanceState);
        }

        View v = inflater.inflate(R.layout.fragment_swipelist, container, false);

        swipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeResources(swipeRefreshColor);
        swipeRefreshLayout.setOnRefreshListener(this);

        unreadNotifierView = v.findViewById(R.id.unreadNotifier);
        tvUnreadCount = (TextView) unreadNotifierView.findViewById(R.id.textView);

        View swipeActionStatusView = v.findViewById(R.id.swipeActionStatusFrame);
        if (swipeActionStatusView != null) {
            swipeActionStatusView.setVisibility(View.INVISIBLE);
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listView = getListView();

        footerView = getActivity().getLayoutInflater().inflate(R.layout.row_loading, null);
        footerProgress = footerView.findViewById(R.id.pbLoading);
        footerText = (TextView) footerView.findViewById(R.id.tvLoading);
        getListView().addFooterView(footerView);

        if (elementClass != null) {
            tweetAdapter = new TweetAdapter(getActivity(), users, null, new AbstractList<Status>() {
                @Override
                public Status get(int index) {
                    twitter4j.Status status = (twitter4j.Status) elements.get(index);
                    return new TwitterStatus(status, (status instanceof PreformedStatus) ? ((PreformedStatus) status).getRepresentUser() : new AuthUserRecord(new AccessToken("", "")));
                }

                @Override
                public int size() {
                    return elements.size();
                }
            });
            setListAdapter(tweetAdapter);
        }

        connection.connect(getActivity());

        if (unreadNotifierView != null) {
            if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light").endsWith("dark")) {
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_material_dark);
            } else {
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_material_light);
            }

            unreadNotifierView.setOnClickListener(v -> scrollToOldestUnread());

            getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    switch (scrollState) {
                        case SCROLL_STATE_FLING:
                            if (USE_INSERT_LOG) Log.d("onScrollStateChanged", "scrollStateChanged = FLING");
                            // ユーザ操作によるスクロールが行われる時は、スクロールロックを解除
                            lockedScrollId = -1;
                            break;
                        case SCROLL_STATE_IDLE:
                            if (USE_INSERT_LOG) Log.d("onScrollStateChanged", "scrollStateChanged = IDLE");
                            break;
                        case SCROLL_STATE_TOUCH_SCROLL:
                            if (USE_INSERT_LOG) Log.d("onScrollStateChanged", "scrollStateChanged = TOUCH_SCROLL");
                            break;
                        default:
                            if (USE_INSERT_LOG) Log.d("onScrollStateChanged", "scrollStateChanged = " + scrollState);
                            break;
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (USE_INSERT_LOG) {
                        Log.d("onScroll", "called. lockedScrollId = " + lockedScrollId + ", y = " + lockedYPosition);
                    }

                    if (elementClass != null) {
                        for (; firstVisibleItem < firstVisibleItem + visibleItemCount && firstVisibleItem < elements.size(); ++firstVisibleItem) {
                            T element = elements.get(firstVisibleItem);
                            long id = commonDelegate.getId(element);
                            if (element != null && unreadSet.contains(id)) {
                                unreadSet.remove(id);
                            }
                        }
                        updateUnreadNotifier();
                    }
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (elementClass != null && lastShowedFirstItemId > -1) {
            int position;
            int length = elements.size();
            for (position = 0; position < length; ++position) {
                if (commonDelegate.getId(elements.get(position)) == lastShowedFirstItemId) break;
            }
            if (position < length) {
                listView.setSelectionFromTop(position, lastShowedFirstItemY);
            }
            updateUnreadNotifier();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!(getActivity() instanceof MainActivity)) {
            if (getActivity() instanceof AppCompatActivity) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(title);
            } else {
                getActivity().setTitle(title);
            }
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        limitedTimeline = sharedPreferences.getBoolean("pref_limited_timeline", false);

        enableDoubleClickBlocker = sharedPreferences.getBoolean("pref_block_doubleclock", false);
        blockingDoubleClick = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        lastShowedFirstItemId = listView.getItemIdAtPosition(listView.getFirstVisiblePosition());
        View topView = listView.getChildAt(0);
        if (topView != null) {
            lastShowedFirstItemY = topView.getTop();
        } else {
            lastShowedFirstItemY = 0;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        setListAdapter(null);
        listView = null;
        tweetAdapter = null;
        swipeRefreshLayout = null;
        unreadNotifierView = null;
        tvUnreadCount = null;
        footerView = null;
        footerProgress = null;
        footerText = null;
        connection.disconnect(getActivity());

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).unregisterTwitterFragment(getArguments().getLong(EXTRA_ID));
        }
    }

    public long getTabId() {
        return getArguments().getLong(EXTRA_ID);
    }

    public String getTitle() {
        return title;
    }

    public AuthUserRecord getCurrentUser() {
        if (users != null && !users.isEmpty()) {
            return users.get(0);
        }
        else return null;
    }

    public List<AuthUserRecord> getBoundUsers() {
        return users != null ? users : new ArrayList<>();
    }

    public int getMode() {
        return mode;
    }

    @Override
    public TwitterService getTwitterService() {
        return connection.getTwitterService();
    }

    @Override
    public boolean isTwitterServiceBound() {
        return connection.isServiceBound();
    }

    protected Handler getHandler() {
        return handler;
    }

    public boolean isLimitedTimeline() {
        return limitedTimeline;
    }

    public void setLimitedTimeline(boolean limitedTimeline) {
        this.limitedTimeline = limitedTimeline;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(int limitCount) {
        this.limitCount = limitCount;
    }

    public void addLimitCount(int limitCount) {
        this.limitCount += limitCount;
    }

    protected SwipeRefreshLayout getSwipeRefreshLayout() {
        return swipeRefreshLayout;
    }

    @Override
    public void scrollToTop() {
        try {
            getListView().setSelection(0);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (getActivity() != null && getActivity().getApplicationContext() != null) {
                Toast.makeText(getActivity().getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void scrollToBottom() {
        try {
            getListView().setSelection(getListAdapter().getCount() - 1);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (getActivity() != null && getActivity().getApplicationContext() != null) {
                Toast.makeText(getActivity().getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void scrollToOldestUnread() {
        try {
            if (unreadSet.isEmpty()) {
                getListView().setSelection(0);
            } else {
                Long lastUnreadId = unreadSet.min();
                int position;
                for (position = 0; position < elements.size(); ++position) {
                    if (commonDelegate.getId(elements.get(position)) == lastUnreadId) break;
                }
                if (position < elements.size()) {
                    getListView().setSelection(position);
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (getActivity() != null && getActivity().getApplicationContext() != null) {
                Toast.makeText(getActivity().getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void scrollToPrevPage() {
        try {
            ListView listView = getListView();
            listView.smoothScrollBy(-listView.getHeight(), 100);
            listView.setSelection(listView.getFirstVisiblePosition());
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (getActivity() != null && getActivity().getApplicationContext() != null) {
                Toast.makeText(getActivity().getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void scrollToNextPage() {
        try {
            ListView listView = getListView();
            listView.smoothScrollBy(listView.getHeight(), 100);
            listView.setSelection(listView.getFirstVisiblePosition());
        } catch (IllegalStateException e) {
            e.printStackTrace();
            if (getActivity() != null && getActivity().getApplicationContext() != null) {
                Toast.makeText(getActivity().getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected void changeFooterProgress(boolean isLoading) {
        this.isLoading = isLoading;
        if (isLoading) {
            if (footerProgress != null) footerProgress.setVisibility(View.VISIBLE);
            if (footerText != null) footerText.setText("loading");
        }
        else {
            if (footerProgress != null) footerProgress.setVisibility(View.INVISIBLE);
            if (footerText != null) footerText.setText("more");
        }
    }

    protected void removeFooter() {
        if (listView != null) {
            listView.removeFooterView(footerView);
            notifyDataSetChanged();
        }
    }

    @Override
    public final void onListItemClick(ListView l, View v, int position, long id) {
        if (blockingDoubleClick) {
            return;
        }

        if (position < elements.size()) {
            //要素クリックイベントの呼び出し
            if (onListItemClick(position, elements.get(position)) && enableDoubleClickBlocker) {
                //次回onResumeまでクリックイベントを無視する
                blockingDoubleClick = true;
            }
        } else if (position == elements.size() && !isLoading) {
            //フッタークリック
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_MORE, user);
            }
        }
    }

    public abstract boolean onListItemClick(int position, T clickedElement);

    protected abstract void executeLoader(int requestMode, AuthUserRecord userRecord);

    public void onServiceConnected() {
        if (users.isEmpty()) {
            users.addAll(getTwitterService().getUsers());
        }
        if (tweetAdapter != null) {
            tweetAdapter.setUserExtras(getTwitterService().getUserExtras());
            tweetAdapter.setStatusLoader(getTwitterService().getStatusLoader());
        }
        limitCount = users.size() * 256;

        getTwitterService().getTimelineHub().addObserver(this);
    }

    protected PrepareInsertResult prepareInsertStatus(T status) {
        //挿入位置の探索と追加
        T storedStatus;
        for (int i = 0; i < elements.size(); ++i) {
            storedStatus = elements.get(i);
            if (commonDelegate.getId(status) == commonDelegate.getId(storedStatus)) {
                return new PrepareInsertResult(PREPARE_INSERT_DUPLICATED, PREPARE_INSERT_DUPLICATED);
            }
            else if (commonDelegate.getId(status) > commonDelegate.getId(storedStatus)) {
                return new PrepareInsertResult(PREPARE_INSERT_ALLOWED, i);
            }
        }
        return new PrepareInsertResult(PREPARE_INSERT_ALLOWED, elements.size());
    }

    @UiThread
    protected void insertElement(T element) {
        insertElement(element, false);
    }

    @UiThread
    protected void insertElement(T element, boolean useScrollLock) {
        PrepareInsertResult prepareInsert = prepareInsertStatus(element);
        int position = prepareInsert.position;
        if (prepareInsert.resultCode == PREPARE_INSERT_DUPLICATED) {
            return;
        } else if (prepareInsert.resultCode == PREPARE_INSERT_MERGED) {
            notifyDataSetChanged();
        } else if (!elements.contains(element)) {
            if (position < elements.size()) {
                boolean isFake = isFakeStatus(element);
                if (!isFake && commonDelegate.getId(elements.get(position)) == commonDelegate.getId(element))
                    return;
            }
            elements.add(position, element);
            notifyDataSetChanged();
            if (isLimitedTimeline() && elements.size() > getLimitCount()) {
                for (ListIterator<T> iterator = elements.listIterator(getLimitCount()); iterator.hasNext(); ) {
                    T e = iterator.next();
                    if (!isFakeStatus(e)) {
                        unreadSet.remove(commonDelegate.getId(e));
                        iterator.remove();
                        notifyDataSetChanged();
                    }
                }
            }

        }

        if (listView == null) {
            Log.w("insertElement", "ListView is null. DROPPED! (" + element + ", " + position + ")");
            return;
        }
        int firstPos = listView.getFirstVisiblePosition();
        View firstView = listView.getChildAt(0);
        int y = firstView != null ? firstView.getTop() : 0;
        if (!useScrollLock && (elements.size() == 1 || firstPos == 0 && y > -1)) {
            listView.setSelection(0);

            if (USE_INSERT_LOG) {
                Log.d("insertElement", "Scroll Position = 0 (Top) ... " + element);
            }
        } else if (lockedScrollId > -1) {
            for (int i = 0; i < elements.size(); i++) {
                if (commonDelegate.getId(elements.get(i)) <= lockedScrollId) {
                    listView.setSelectionFromTop(i, y);
                    if (position < i) {
                        unreadSet.add(commonDelegate.getId(element));
                    }

                    scrollUnlockHandler.removeCallbacksAndMessages(null);
                    scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, i, y), 200);

                    if (USE_INSERT_LOG) {
                        Log.d("insertElement", "Scroll Position = " + i + " (Locked strict) ... " + element);
                        dumpAroundTweets(i);
                    }
                    break;
                }
            }
        } else if (position <= firstPos) {
            if (elements.size() <= 1 && isFakeStatus(element)) {
                // 要素数1の時にマーカーを掴むとずっと下にスクロールされてしまうので回避する
            } else {
                int lockedPosition = firstPos + 1;
                if (lockedPosition < elements.size()) {
                    if (isFakeStatus(elements.get(lockedPosition))) {
                        lockedPosition = firstPos;
                    }
                } else {
                    lockedPosition = firstPos;
                }

                unreadSet.add(commonDelegate.getId(element));
                listView.setSelectionFromTop(lockedPosition, y);

                lockedScrollId = commonDelegate.getId(elements.get(lockedPosition));
                lockedYPosition = y;

                scrollUnlockHandler.removeCallbacksAndMessages(null);
                scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, firstPos + 1, y), 200);

                if (USE_INSERT_LOG) {
                    Log.d("insertElement", "Scroll Position = " + lockedPosition + " (Locked) ... " + element);
                    dumpAroundTweets(firstPos);
                    Log.d("insertElement", "    lockedScrollId = " + lockedScrollId + " ... " + elements.get(lockedPosition));
                }
            }
        } else {
            if (USE_INSERT_LOG) {
                Log.d("insertElement", "Scroll Position = " + firstPos + " (Not changed) ... " + element);
                dumpAroundTweets(firstPos);
            }
        }
        updateUnreadNotifier();
    }

    protected void deleteElement(long id) {
        if (listView == null) {
            Log.w("deleteElement", "ListView is null. DROPPED! (ID: " + id + ")");
            return;
        }
        Iterator<T> iterator = elements.iterator();
        while (iterator.hasNext()) {
            if (commonDelegate.getId(iterator.next()) == id) {
                int firstPos = listView.getFirstVisiblePosition();
                long firstId = listView.getItemIdAtPosition(firstPos);
                View firstView = listView.getChildAt(0);
                int y = firstView != null? firstView.getTop() : 0;
                iterator.remove();
                notifyDataSetChanged();
                if (elements.size() == 1 || firstPos == 0) {
                    listView.setSelection(0);
                } else if (lockedScrollId > -1) {
                    for (int i = 0; i < elements.size(); i++) {
                        if (commonDelegate.getId(elements.get(i)) <= lockedScrollId) {
                            listView.setSelectionFromTop(i, y);
                            break;
                        }
                    }
                } else {
                    listView.setSelectionFromTop(firstPos - (firstId < id? 1 : 0), y);
                }
                if (unreadSet.contains(id)) {
                    unreadSet.remove(id);
                    updateUnreadNotifier();
                }
                break;
            }
        }
    }

    protected void notifyDataSetChanged() {
        if (tweetAdapter != null && getActivity() != null) {
            getActivity().runOnUiThread(tweetAdapter::notifyDataSetChanged);
        }
    }

    @Override
    public void onRefresh() {
        for (AuthUserRecord user : users) {
            executeLoader(LOADER_LOAD_UPDATE, user);
            ++refreshCounter;
        }
    }

    protected void setRefreshComplete() {
        if (--refreshCounter < 1 && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    protected void clearUnreadNotifier() {
        unreadSet.clear();
        updateUnreadNotifier();
    }

    protected void updateUnreadNotifier() {
        if (unreadNotifierView == null) return;
        if (unreadSet.size() < 1) {
            unreadNotifierView.setVisibility(View.INVISIBLE);
            return;
        }
        if (tvUnreadCount != null) {
            tvUnreadCount.setText("新着 " + unreadSet.size() + "件");
        }

        unreadNotifierView.setVisibility(View.VISIBLE);
    }

    protected void disableReload() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
            disabledReload = true;
        }
    }

    protected void disableReloadTemp() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
        }
    }

    protected void enableReload() {
        if (swipeRefreshLayout != null && !disabledReload) {
            swipeRefreshLayout.setEnabled(true);
        }
    }

    protected void setBlockingDoubleClick(boolean blockingDoubleClick) {
        this.blockingDoubleClick = blockingDoubleClick;
    }

    private static boolean isFakeStatus(TwitterResponse element) {
        return false;
    }

    private void dumpAroundTweets(int position) {
        if (position > 0) {
            Log.d("dumpAroundTweets", "    " + (position - 1) + " : ... " + elements.get(position - 1));
        }
        Log.d("dumpAroundTweets", "    " + position + " : ... " + elements.get(position));
        if (position + 1 < elements.size()) {
            Log.d("dumpAroundTweets", "    " + (position + 1) + " : ... " + elements.get(position + 1));
        }
    }

    public String getRestTag() {
        return "";
    }

    public String getSubscribeIdentifier() {
        Bundle args = getArguments();
        if (args.containsKey(TwitterListFragment.EXTRA_ID)) {
            return String.valueOf(args.getLong(TwitterListFragment.EXTRA_ID));
        }
        return this.toString();
    }

    @NotNull
    @Override
    public String getTimelineId() {
        return getSubscribeIdentifier();
    }

    @Override
    public void onTimelineEvent(@NotNull TimelineEvent event) {}

    /**
     * {@link TwitterListFragment#prepareInsertStatus(TwitterResponse)} の結果を格納します。
     */
    protected static class PrepareInsertResult {
        /** 処理結果を表します。*/
        @PrepareInsertResultCode public int resultCode;

        /** 要素の挿入先positionを表します。 */
        public int position;

        public PrepareInsertResult(int resultCode, int position) {
            this.resultCode = resultCode;
            this.position = position;
        }
    }

    /**
     * {@link TwitterListFragment#prepareInsertStatus(TwitterResponse)} の処理結果を表します。
     */
    @IntDef({PREPARE_INSERT_ALLOWED, PREPARE_INSERT_DUPLICATED, PREPARE_INSERT_MERGED})
    protected @interface PrepareInsertResultCode {}
}
