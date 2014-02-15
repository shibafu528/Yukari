package shibafu.yukari.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import twitter4j.Twitter;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TweetListFragment extends AttachableListFragment {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TRACE_START = "trace_start";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int LOADER_LOAD_INIT   = 0;
    public static final int LOADER_LOAD_MORE   = 1;
    public static final int LOADER_LOAD_UPDATE = 2;

    //Statuses List
    protected LinkedList<PreformedStatus> statuses = new LinkedList<PreformedStatus>();
    protected ListView listView;
    protected TweetAdapterWrap adapterWrap;

    //Binding Accounts
    protected List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();

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

    private Handler handler = new Handler();

    public TweetListFragment() {
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

        adapterWrap = new TweetAdapterWrap(getActivity().getApplicationContext(), users, statuses);
        setListAdapter(adapterWrap.getAdapter());
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < statuses.size()) {
                    //ツイート詳細画面の呼び出し
                    PreformedStatus s = statuses.get(position);
                    Intent intent = new Intent(getActivity(), StatusActivity.class);
                    intent.putExtra(StatusActivity.EXTRA_STATUS, s);
                    intent.putExtra(StatusActivity.EXTRA_USER, s.getReceiveUser());
                    startActivity(intent);
                }
                else if (position == statuses.size() && !isLoading) {
                    //フッタークリック
                    for (AuthUserRecord user : users) {
                        executeLoader(LOADER_LOAD_MORE, user);
                    }
                }
            }
        });

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

    protected TwitterService getService() {
        return service;
    }

    protected boolean isServiceBound() {
        return serviceBound;
    }

    protected Handler getHandler() {
        return handler;
    }

    @Override
    public void scrollToTop() {
        getListView().setSelection(0);
    }

    @Override
    public void scrollToBottom() {
        getListView().setSelection(statuses.size() - 1);
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

        if (mode == TabType.TABTYPE_DM) {
            footerProgress.setVisibility(View.INVISIBLE);
            footerText.setText("DM機能は未実装です");
        }
    }

    protected void removeFooter() {
        listView.removeFooterView(footerView);
    }

    protected int prepareInsertStatus(PreformedStatus status) {
        //自己ツイートチェック
        boolean isMyTweet = service.isMyTweet(status) != null;
        //優先ユーザチェック
        if (!isMyTweet) {
            ArrayList<Long> mentions = new ArrayList<Long>();
            for (UserMentionEntity entity : status.getUserMentionEntities()) {
                mentions.add(entity.getId());
            }
            for (AuthUserRecord user : users) {
                //指名されている場合はそちらを優先する
                if (mentions.contains(user.NumericId)) {
                    status.setReceiveUser(user);
                    break;
                }
            }
        }
        //挿入位置の探索と追加
        PreformedStatus storedStatus;
        for (int i = 0; i < statuses.size(); ++i) {
            storedStatus = statuses.get(i);
            if (status.getId() == storedStatus.getId()) {
                //既に他のアカウントで受信されていた場合でも、今回の受信がプライマリアカウントによるものであれば
                //受信アカウントのフィールドを上書きする
                if (!isMyTweet && status.getReceiveUser().isPrimary && !storedStatus.getReceiveUser().isPrimary) {
                    storedStatus.setReceiveUser(status.getReceiveUser());
                }
                return -1;
            }
            else if (status.getId() > storedStatus.getId()) {
                return i;
            }
        }
        return statuses.size();
    }

    protected abstract void executeLoader(int requestMode, AuthUserRecord userRecord);

    protected abstract void onServiceConnected();

    protected abstract void onServiceDisconnected();

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TweetListFragment.this.service = binder.getService();
            twitter = TweetListFragment.this.service.getTwitter();
            serviceBound = true;

            if (users.isEmpty()) {
                users.addAll(TweetListFragment.this.service.getActiveUsers());
            }

            TweetListFragment.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            TweetListFragment.this.onServiceDisconnected();
        }
    };

    private RESTLoader.RESTLoaderInterface defaultRESTInterface = new RESTLoader.RESTLoaderInterface() {
        @Override
        public List<PreformedStatus> getStatuses() {
            return statuses;
        }

        @Override
        public void notifyDataSetChanged() {
            adapterWrap.notifyDataSetChanged();
        }

        @Override
        public int prepareInsertStatus(PreformedStatus status) {
            return TweetListFragment.this.prepareInsertStatus(status);
        }

        @Override
        public void changeFooterProgress(boolean isLoading) {
            TweetListFragment.this.changeFooterProgress(isLoading);
        }
    };

    protected RESTLoader.RESTLoaderInterface getDefaultRESTInterface() {
        return defaultRESTInterface;
    }

}
