package shibafu.yukari.fragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.AttachableList;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetListFragment extends ListFragment implements TwitterService.StatusListener, AttachableList {

    public static final int MODE_EMPTY = 0;
    public static final int MODE_HOME = 1;
    public static final int MODE_MENTION = 2;
    public static final int MODE_DM = 3;
    public static final int MODE_USER = 4;
    public static final int MODE_TRACE = 5;
    public static final int MODE_FAVORITE = 6;

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
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        mode = args.getInt(EXTRA_MODE, MODE_EMPTY);
        if (mode == MODE_TRACE) {
            traceStart = (Status) args.getSerializable(EXTRA_TRACE_START);
            prepareInsertStatus(new PreformedStatus(traceStart, user));
        }
        else if (mode == MODE_USER || mode == MODE_FAVORITE) {
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
        changeFooterProgress(true);

        adapterWrap = new TweetAdapterWrap(getActivity().getApplicationContext(), users, statuses);
        setListAdapter(adapterWrap.getAdapter());
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < statuses.size()) {
                    //アニメーションのスケジュール
                    final View v = view;
                    view.setBackgroundColor(Color.parseColor("#B394E0"));
                    Timer t = new Timer();
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    v.setBackgroundColor((Integer)v.getTag());
                                }
                            });
                        }
                    }, new Date(System.currentTimeMillis() + 100));
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
                        case MODE_HOME:
                        case MODE_MENTION:
                        case MODE_DM:
                        case MODE_USER:
                        case MODE_FAVORITE:
                            for (final AuthUserRecord user : users) {
                                AsyncTask<Void, Void, ResponseList<Status>> task = new AsyncTask<Void, Void, ResponseList<Status>>() {
                                    @Override
                                    protected ResponseList<twitter4j.Status> doInBackground(Void... params) {
                                        twitter.setOAuthAccessToken(user.getAccessToken());
                                        try {
                                            ResponseList<twitter4j.Status> responseList = null;
                                            Paging paging = new Paging();
                                            paging.setMaxId(lastStatusId - 1);
                                            switch (mode) {
                                                case MODE_HOME:
                                                    responseList = twitter.getHomeTimeline(paging);
                                                    break;
                                                case MODE_USER:
                                                    responseList = twitter.getUserTimeline(targetUser.getId(), paging);
                                                    break;
                                                case MODE_FAVORITE:
                                                    responseList = twitter.getFavorites(targetUser.getId(), paging);
                                                    break;
                                            }
                                            if (responseList == null) {
                                                //lastStatusId = -1;
                                            }
                                            else if (responseList.size() > 0) {
                                                lastStatusId = responseList.get(responseList.size() - 1).getId();
                                            }
                                            return responseList;
                                        } catch (TwitterException e) {
                                            e.printStackTrace();
                                        }
                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(ResponseList<twitter4j.Status> statuses) {
                                        if (statuses != null) {
                                            PreformedStatus ps;
                                            int position;
                                            for (twitter4j.Status status : statuses) {
                                                ps = new PreformedStatus(status, user);
                                                position = prepareInsertStatus(ps);
                                                if (position > -1) {
                                                    TweetListFragment.this.statuses.add(position, ps);
                                                }
                                            }
                                            adapterWrap.notifyDataSetChanged();
                                        }
                                        changeFooterProgress(false);
                                    }
                                };
                                task.execute();
                                changeFooterProgress(true);
                                break;
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
        if (mode == MODE_HOME) {
            getActivity().registerReceiver(onReloadReceiver, new IntentFilter(TwitterService.RELOADED_USERS));
            getActivity().registerReceiver(onActiveChangedReceiver, new IntentFilter(TwitterService.CHANGED_ACTIVE_STATE));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mode == MODE_HOME) {
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

            if (user == null) {
                users.addAll(TweetListFragment.this.service.getActiveUsers());
            }
            else {
                users.add(TweetListFragment.this.service.getPrimaryUser());
            }

            if (mode == MODE_TRACE) {
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
            else for (final AuthUserRecord user : users) {
                AsyncTask<Void, Void, ResponseList<Status>> task = new AsyncTask<Void, Void, ResponseList<Status>>() {
                    @Override
                    protected ResponseList<twitter4j.Status> doInBackground(Void... params) {
                        twitter.setOAuthAccessToken(user.getAccessToken());
                        try {
                            ResponseList<twitter4j.Status> responseList = null;
                            switch (mode) {
                                case MODE_HOME:
                                    responseList = twitter.getHomeTimeline();
                                    break;
                                case MODE_USER:
                                    responseList = twitter.getUserTimeline(targetUser.getId());
                                    break;
                                case MODE_FAVORITE:
                                    responseList = twitter.getFavorites(targetUser.getId());
                                    break;
                            }
                            if (responseList == null) {
                                lastStatusId = -1;
                            }
                            else if (responseList.size() > 0) {
                                lastStatusId = responseList.get(responseList.size() - 1).getId();
                            }
                            return responseList;
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(ResponseList<twitter4j.Status> statuses) {
                        if (statuses != null) {
                            PreformedStatus ps;
                            int position;
                            for (twitter4j.Status status : statuses) {
                                ps = new PreformedStatus(status, user);
                                position = prepareInsertStatus(ps);
                                if (position > -1) {
                                    TweetListFragment.this.statuses.add(position, ps);
                                }
                            }
                            adapterWrap.notifyDataSetChanged();
                        }
                        if (mode == MODE_HOME) {
                            TweetListFragment.this.service.addStatusListener(TweetListFragment.this);
                        }
                        changeFooterProgress(false);
                    }
                };
                task.execute();
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
            final int position = prepareInsertStatus(status);
            if (position > -1) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!statuses.contains(status) && statuses.get(position).getId() != status.getId()) {
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

}
