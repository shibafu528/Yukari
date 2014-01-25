package shibafu.yukari.fragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.AttachableList;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetListFragment extends ListFragment implements TwitterService.StatusListener, AttachableList {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TRACE_START = "trace_start";
    public static final String EXTRA_SHOW_USER = "show_user";

    private LinkedList<PreformedStatus> statuses = new LinkedList<PreformedStatus>();

    private Twitter twitter;
    private TweetAdapterWrap adapterWrap;
    private AuthUserRecord user;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private String title;
    private int mode;

    private Status traceStart = null;
    private User targetUser = null;

    private ListView listView;
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;

    private long lastStatusId = -1;
    private boolean isLoading = false;

    private TwitterService service;
    private boolean serviceBound = false;
    private Handler handler = new Handler();

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
        if (mode == TabType.TABTYPE_TRACE) {
            traceStart = (Status) args.getSerializable(EXTRA_TRACE_START);
            statuses.add(new PreformedStatus(traceStart, user));
        }
        else if (mode == TabType.TABTYPE_USER || mode == TabType.TABTYPE_FAVORITE) {
            targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        }
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
                    switch (mode) {
                        case TabType.TABTYPE_HOME:
                        case TabType.TABTYPE_MENTION:
                        case TabType.TABTYPE_DM:
                        case TabType.TABTYPE_USER:
                        case TabType.TABTYPE_FAVORITE:
                            for (AuthUserRecord user : users) {
                                RESTLoader loader = new RESTLoader();
                                loader.execute(loader.new Params(lastStatusId, user));
                            }
                    }
                }
            }
        });

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mode == TabType.TABTYPE_HOME) {
            getActivity().registerReceiver(onReloadReceiver, new IntentFilter(TwitterService.RELOADED_USERS));
            getActivity().registerReceiver(onActiveChangedReceiver, new IntentFilter(TwitterService.CHANGED_ACTIVE_STATE));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mode == TabType.TABTYPE_HOME) {
            getActivity().unregisterReceiver(onReloadReceiver);
            getActivity().unregisterReceiver(onActiveChangedReceiver);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            service.removeStatusListener(this);
            getActivity().unbindService(connection);
            serviceBound = false;
        }
    }

    public String getTitle() {
        return title;
    }

    public AuthUserRecord getCurrentUser() {
        return user;
    }

    @Override
    public void scrollToTop() {
        listView.setSelection(0);
    }

    @Override
    public void scrollToBottom() {
        listView.setSelection(statuses.size() - 1);
    }

    private void changeFooterProgress(boolean isLoading) {
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
            footerText.setText("DM機能は未実装です");
        }
    }

    private int prepareInsertStatus(PreformedStatus status) {
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

    private BroadcastReceiver onReloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };

    private BroadcastReceiver onActiveChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<AuthUserRecord> inactiveList = (ArrayList<AuthUserRecord>) intent.getSerializableExtra(TwitterService.EXTRA_CHANGED_INACTIVE);
            for (AuthUserRecord inactive : inactiveList) {
                if (users.contains(inactive)) {
                    users.remove(inactive);
                }
            }
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TweetListFragment.this.service = binder.getService();
            twitter = TweetListFragment.this.service.getTwitter();
            serviceBound = true;

            if (users.isEmpty()) {
                if (user == null) {
                    users.addAll(TweetListFragment.this.service.getActiveUsers());
                }
                else {
                    users.add(TweetListFragment.this.service.getPrimaryUser());
                }
            }

            if (mode == TabType.TABTYPE_TRACE) {
                if (user == null) {
                    user = TweetListFragment.this.service.getPrimaryUser();
                }
                AsyncTask<Status, Void, Void> task = new AsyncTask<Status, Void, Void>() {
                    @Override
                    protected Void doInBackground(twitter4j.Status... params) {
                        twitter.setOAuthAccessToken(user.getAccessToken());
                        twitter4j.Status status = params[0];
                        while (status.getInReplyToStatusId() > -1) {
                            try {
                                final twitter4j.Status reply = status = twitter.showStatus(status.getInReplyToStatusId());
                                final PreformedStatus ps = new PreformedStatus(reply, user);
                                final int location = prepareInsertStatus(ps);
                                if (location > -1) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            statuses.add(location, ps);
                                            adapterWrap.notifyDataSetChanged();
                                        }
                                    });
                                }
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        listView.removeFooterView(footerView);
                    }
                };
                task.execute(traceStart);
                changeFooterProgress(true);
            }
            else if (statuses.isEmpty()) {
                for (AuthUserRecord user : users) {
                    RESTLoader loader = new RESTLoader();
                    loader.execute(loader.new Params(user));
                }
            }

            if (mode == TabType.TABTYPE_HOME || mode == TabType.TABTYPE_MENTION) {
                TweetListFragment.this.service.addStatusListener(TweetListFragment.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status) {
        if (users.contains(from) && !statuses.contains(status)) {
            if (mode == TabType.TABTYPE_MENTION &&
                    ( !status.isMentionedToMe() || status.isRetweet() )) return;

            final int position = prepareInsertStatus(status);
            if (position > -1) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!statuses.contains(status)) {
                            if (position < statuses.size())  {
                                if (statuses.get(position).getId() == status.getId()) return;
                            }
                            statuses.add(position, status);
                            adapterWrap.notifyDataSetChanged();
                            if (statuses.size() == 1 || listView.getFirstVisiblePosition() < 2) {
                                listView.setSelection(0);
                            } else {
                                listView.setSelection(listView.getFirstVisiblePosition() + 1);
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {
        //TODO: DM受信時の処理
    }

    @Override
    public void onDelete(AuthUserRecord from, final StatusDeletionNotice statusDeletionNotice) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Iterator<PreformedStatus> iterator = statuses.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getId() == statusDeletionNotice.getStatusId()) {
                        iterator.remove();
                        adapterWrap.notifyDataSetChanged();
                        break;
                    }
                }
            }
        });
    }

    private class RESTLoader extends AsyncTask<RESTLoader.Params, Void, PreformedResponseList<PreformedStatus>> {
        class Params {
            private Paging paging;
            private AuthUserRecord userRecord;

            public Params(AuthUserRecord userRecord) {
                this.paging = new Paging();
                this.userRecord = userRecord;
            }

            public Params(long lastStatusId, AuthUserRecord userRecord) {
                this.paging = new Paging();
                if (lastStatusId > -1) {
                    paging.setMaxId(lastStatusId - 1);
                }
                this.userRecord = userRecord;
            }

            public Paging getPaging() {
                return paging;
            }

            public AuthUserRecord getUserRecord() {
                return userRecord;
            }
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
            try {
                ResponseList<twitter4j.Status> responseList = null;
                Paging paging = params[0].getPaging();
                paging.setCount(60);
                switch (mode) {
                    case TabType.TABTYPE_HOME:
                        responseList = twitter.getHomeTimeline(paging);
                        break;
                    case TabType.TABTYPE_MENTION:
                        responseList = twitter.getMentionsTimeline(paging);
                        break;
                    case TabType.TABTYPE_USER:
                        responseList = twitter.getUserTimeline(targetUser.getId(), paging);
                        break;
                    case TabType.TABTYPE_FAVORITE:
                        responseList = twitter.getFavorites(targetUser.getId(), paging);
                        break;
                }
                if (responseList == null) {
                    lastStatusId = -1;
                }
                else if (responseList.size() > 0) {
                    lastStatusId = responseList.get(responseList.size() - 1).getId();
                }
                return PRListFactory.create(responseList, params[0].getUserRecord());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            changeFooterProgress(true);
        }

        @Override
        protected void onPostExecute(PreformedResponseList<PreformedStatus> result) {
            if (result != null) {
                int position;
                for (PreformedStatus status : result) {
                    position = prepareInsertStatus(status);
                    if (position > -1) {
                        statuses.add(position, status);
                    }
                }
                adapterWrap.notifyDataSetChanged();
            }
            changeFooterProgress(false);
        }
    }

}
