package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import twitter4j.Twitter;
import twitter4j.TwitterResponse;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TwitterListFragment<T extends TwitterResponse> extends ListFragment implements TwitterServiceConnection.ServiceConnectionCallback {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int LOADER_LOAD_INIT   = 0;
    public static final int LOADER_LOAD_MORE   = 1;
    public static final int LOADER_LOAD_UPDATE = 2;

    //Elements List
    protected ArrayList<T> elements = new ArrayList<>();
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
    private TwitterServiceConnection connection = new TwitterServiceConnection(this);

    //TweetCommon Delegate
    private TweetCommonDelegate commonDelegate;

    private Handler handler = new Handler();

    public TwitterListFragment() {
        setRetainInstance(true);
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
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        listView = getListView();

        footerView = getActivity().getLayoutInflater().inflate(R.layout.row_loading, null);
        footerProgress = (ProgressBar) footerView.findViewById(R.id.pbLoading);
        footerText = (TextView) footerView.findViewById(R.id.tvLoading);
        getListView().addFooterView(footerView);

        connection.connect(getActivity());
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

    public int getMode() {
        return mode;
    }

    public abstract boolean isCloseable();

    protected TwitterService getService() {
        return connection.getTwitterService();
    }

    protected boolean isServiceBound() {
        return connection.isServiceBound();
    }

    protected StatusManager getStatusManager() {
        return getService().getStatusManager();
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

    public void onServiceConnected() {
        twitter = getService().getTwitter();
        if (users.isEmpty()) {
            users.addAll(getService().getUsers());
        }
    }

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

}
