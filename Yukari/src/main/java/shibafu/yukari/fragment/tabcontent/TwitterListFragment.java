package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import twitter4j.Twitter;
import twitter4j.TwitterResponse;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TwitterListFragment<T extends TwitterResponse> extends ListFragment implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate{

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int LOADER_LOAD_INIT   = 0;
    public static final int LOADER_LOAD_MORE   = 1;
    public static final int LOADER_LOAD_UPDATE = 2;

    //Elements List
    private Class<T> elementClass;
    protected ArrayList<T> elements = new ArrayList<>();
    protected ListView listView;
    protected TweetAdapterWrap adapterWrap;

    //Elements Limit
    private boolean limitedTimeline;
    private int limitCount = 256;

    //Unread Set
    private long lastShowedFirstItemId = -1;
    private int lastShowedFirstItemY = 0;
    private View unreadNotifierView;
    private Set<Long> unreadSet = new HashSet<>();

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

    //Footer View
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;
    private boolean isLoading = false;

    //Twitter Wrapper
    protected Twitter twitter;
    private TwitterServiceConnection connection = new TwitterServiceConnection(this);

    //TweetCommon Delegate
    private TweetCommonDelegate commonDelegate;

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
        swipeRefreshLayout.setColorSchemeResources(R.color.key_color);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                for (AuthUserRecord user : users) {
                    executeLoader(LOADER_LOAD_UPDATE, user);
                    ++refreshCounter;
                }
            }
        });

        unreadNotifierView = v.findViewById(R.id.unreadNotifier);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        listView = getListView();

        footerView = getActivity().getLayoutInflater().inflate(R.layout.row_loading, null);
        footerProgress = (ProgressBar) footerView.findViewById(R.id.pbLoading);
        footerText = (TextView) footerView.findViewById(R.id.tvLoading);
        getListView().addFooterView(footerView);

        if (elementClass != null) {
            adapterWrap = new TweetAdapterWrap(getActivity(), users, null, elements, elementClass);
            setListAdapter(adapterWrap.getAdapter());
        }

        connection.connect(getActivity());

        if (unreadNotifierView != null) {
            switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
                case "light":
                    unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_light);
                    break;
                case "dark":
                    unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_dark);
                    break;
            }

            unreadNotifierView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (unreadSet.isEmpty()) {
                        listView.setSelection(0);
                    } else {
                        Long lastUnreadId = Collections.min(unreadSet);
                        int position;
                        for (position = 0; position < elements.size(); ++position) {
                            if (commonDelegate.getId(elements.get(position)) == lastUnreadId) break;
                        }
                        if (position < elements.size()) {
                            listView.setSelection(position);
                        }
                    }
                }
            });

            getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (elementClass != null) {
                        for (; firstVisibleItem < firstVisibleItem + visibleItemCount && firstVisibleItem < elements.size(); ++firstVisibleItem) {
                            T element = elements.get(firstVisibleItem);
                            if (element != null && unreadSet.contains(commonDelegate.getId(element))) {
                                unreadSet.remove(commonDelegate.getId(element));
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
            final Long[] copyOfUnreadSet = unreadSet.toArray(new Long[unreadSet.size()]);
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    int position;
                    int length = elements.size();
                    for (position = 0; position < length; ++position) {
                        if (commonDelegate.getId(elements.get(position)) == lastShowedFirstItemId) break;
                    }
                    if (position < length) {
                        listView.setSelectionFromTop(position, lastShowedFirstItemY);
                    }
                    Collections.addAll(unreadSet, copyOfUnreadSet);
                    updateUnreadNotifier();
                }
            }, 150);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!(getActivity() instanceof MainActivity)) {
            if (getActivity() instanceof ActionBarActivity) {
                ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(title);
            } else {
                getActivity().setTitle(title);
            }
        }

        limitedTimeline = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_limited_timeline", false);
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
    public void onDestroy() {
        super.onDestroy();
        connection.disconnect(getActivity());
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
        return users != null ? users : new ArrayList<AuthUserRecord>();
    }

    public int getMode() {
        return mode;
    }

    public abstract boolean isCloseable();

    protected TwitterService getService() {
        return connection.getTwitterService();
    }

    @Override
    public TwitterService getTwitterService() {
        return connection.getTwitterService();
    }

    protected boolean isServiceBound() {
        return connection.isServiceBound();
    }

    @Override
    public boolean isTwitterServiceBound() {
        return connection.isServiceBound();
    }

    protected StatusManager getStatusManager() {
        return getService().getStatusManager();
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

    public void scrollToTop() {
        getListView().setSelection(0);
    }

    public void scrollToBottom() {
        getListView().setSelection(getListAdapter().getCount() - 1);
    }

    protected void changeFooterProgress(boolean isLoading) {
        this.isLoading = isLoading;
        if (isLoading) {
            footerProgress.setVisibility(View.VISIBLE);
            footerText.setText("loading");
        }
        else {
            footerProgress.setVisibility(View.INVISIBLE);
            footerText.setText("more");
        }
    }

    protected void removeFooter() {
        listView.removeFooterView(footerView);
    }

    @Override
    public final void onListItemClick(ListView l, View v, int position, long id) {
        if (position < elements.size()) {
            //要素クリックイベントの呼び出し
            onListItemClick(elements.get(position));
        }
        else if (position == elements.size() && !isLoading) {
            //フッタークリック
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_MORE, user);
            }
        }
    }

    public abstract void onListItemClick(T clickedElement);

    protected abstract void executeLoader(int requestMode, AuthUserRecord userRecord);

    public void onServiceConnected() {
        twitter = getService().getTwitter();
        if (users.isEmpty()) {
            users.addAll(getService().getUsers());
        }
        if (adapterWrap != null) {
            adapterWrap.setUserExtras(getService().getUserExtras());
        }
        limitCount = users.size() * 256;
    }

    protected int prepareInsertStatus(T status) {
        //挿入位置の探索と追加
        T storedStatus;
        for (int i = 0; i < elements.size(); ++i) {
            storedStatus = elements.get(i);
            if (commonDelegate.getId(status) == commonDelegate.getId(storedStatus)) {
                return -1;
            }
            else if (commonDelegate.getId(status) > commonDelegate.getId(storedStatus)) {
                return i;
            }
        }
        return elements.size();
    }

    protected void insertElement(T element, int position) {
        if (!elements.contains(element)) {
            if (position < elements.size()) {
                if (commonDelegate.getId(elements.get(position)) == commonDelegate.getId(element))
                    return;
            }
            elements.add(position, element);
            if (isLimitedTimeline() && elements.size() > getLimitCount()) {
                for (ListIterator<T> iterator = elements.listIterator(getLimitCount()); iterator.hasNext(); ) {
                    unreadSet.remove(commonDelegate.getId(iterator.next()));
                    iterator.remove();
                }
            }
            int firstPos = listView.getFirstVisiblePosition();
            View firstView = listView.getChildAt(0);
            int y = firstView != null? firstView.getTop() : 0;
            adapterWrap.notifyDataSetChanged();
            if (elements.size() == 1 || firstPos == 0 && y > -1) {
                listView.setSelection(0);
            } else {
                unreadSet.add(commonDelegate.getId(element));
                listView.setSelectionFromTop(firstPos + 1, y);
            }
            updateUnreadNotifier();
        }
    }

    protected void deleteElement(TwitterResponse element) {
        long id = TweetCommon.newInstance(element.getClass()).getId(element);
        Iterator<T> iterator = elements.iterator();
        while (iterator.hasNext()) {
            if (commonDelegate.getId(iterator.next()) == id) {
                int firstPos = listView.getFirstVisiblePosition();
                long firstId = listView.getItemIdAtPosition(firstPos);
                View firstView = listView.getChildAt(0);
                int y = firstView != null? firstView.getTop() : 0;
                iterator.remove();
                adapterWrap.notifyDataSetChanged();
                if (elements.size() == 1 || firstPos == 0) {
                    listView.setSelection(0);
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
        TextView tv = (TextView) unreadNotifierView.findViewById(R.id.textView);
        tv.setText(String.format("新着 %d件", unreadSet.size()));

        unreadNotifierView.setVisibility(View.VISIBLE);
    }

    protected void disableReload() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
        }
    }

}
