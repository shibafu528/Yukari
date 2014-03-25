package shibafu.yukari.fragment.tabcontent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import twitter4j.Twitter;
import twitter4j.TwitterResponse;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TwitterListFragment<T extends TwitterResponse> extends ListFragment {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int LOADER_LOAD_INIT   = 0;
    public static final int LOADER_LOAD_MORE   = 1;
    public static final int LOADER_LOAD_UPDATE = 2;

    //Elements List
    protected LinkedList<T> elements = new LinkedList<>();
    protected ListView listView;

    //Binding Accounts
    protected List<AuthUserRecord> users = new ArrayList<>();

    //Fragment States
    private String title;
    private int mode;

    //Footer View
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;
    private boolean isLoading = false;

    //Twitter Wrapper
    protected Twitter twitter;
    private TwitterService service;
    private boolean serviceBound = false;

    //TweetCommon Delegate
    private TweetCommonDelegate commonDelegate;

    private Handler handler = new Handler();

    public TwitterListFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        AuthUserRecord manager = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        if (manager != null) {
            users.add(manager);
        }
        mode = args.getInt(EXTRA_MODE, -1);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        listView = getListView();

        footerView = getActivity().getLayoutInflater().inflate(R.layout.row_loading, null);
        footerProgress = (ProgressBar) footerView.findViewById(R.id.pbLoading);
        footerText = (TextView) footerView.findViewById(R.id.tvLoading);
        getListView().addFooterView(footerView);

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            getActivity().unbindService(connection);
            serviceBound = false;
        }
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

    public int getMode() {
        return mode;
    }

    public abstract boolean isCloseable();

    protected TwitterService getService() {
        return service;
    }

    protected StatusManager getStatusManager() {
        return service.getStatusManager();
    }

    protected boolean isServiceBound() {
        return serviceBound;
    }

    protected Handler getHandler() {
        return handler;
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

    protected abstract void onServiceConnected();

    protected abstract void onServiceDisconnected();

    protected int prepareInsertStatus(T status) {
        if (commonDelegate == null) {
            commonDelegate = TweetCommon.newInstance(status.getClass());
        }
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TwitterListFragment.this.service = binder.getService();
            twitter = TwitterListFragment.this.service.getTwitter();
            serviceBound = true;

            if (users.isEmpty()) {
                users.addAll(TwitterListFragment.this.service.getActiveUsers());
            }

            TwitterListFragment.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            TwitterListFragment.this.onServiceDisconnected();
        }
    };

}
