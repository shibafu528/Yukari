package shibafu.yukari.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import java.util.Date;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetListFragment extends ListFragment implements TwitterService.StatusListener {

    public static final int MODE_EMPTY = 0;
    public static final int MODE_HOME = 1;
    public static final int MODE_MENTION = 2;
    public static final int MODE_DM = 3;
    public static final int MODE_USER = 4;
    public static final int MODE_TRACE = 5;

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TRACE_START = "trace_start";
    public static final String EXTRA_SHOW_USER = "show_user";

    private LinkedList<Status> statuses = new LinkedList<Status>();

    private Twitter twitter;
    private TweetAdapterWrap adapterWrap;
    private AuthUserRecord user;
    private String title;
    private int mode;

    private Status traceStart = null;
    private User targetUser = null;

    private ListView listView;
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;

    private long lastStatusId = -1;

    private TwitterService service;
    private boolean serviceBound = false;
    private Handler handler = new Handler();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        mode = args.getInt(EXTRA_MODE, MODE_EMPTY);
        if (mode == MODE_TRACE) {
            traceStart = (Status) args.getSerializable(EXTRA_TRACE_START);
            statuses.add(traceStart);
        }
        else if (mode == MODE_USER) {
            targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        }
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        twitter = TwitterUtil.getTwitterInstance(getActivity());
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

        adapterWrap = new TweetAdapterWrap(getActivity().getApplicationContext(), user, statuses);
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
                    Status s = statuses.get(position);
                    Intent intent = new Intent(getActivity(), StatusActivity.class);
                    intent.putExtra(StatusActivity.EXTRA_STATUS, s);
                    intent.putExtra(StatusActivity.EXTRA_USER, user);
                    startActivity(intent);
                }
            }
        });

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
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

    private void changeFooterProgress(boolean isLoading) {
        if (isLoading) {
            footerProgress.setVisibility(View.VISIBLE);
            footerText.setText("loading");
        }
        else {
            footerProgress.setVisibility(View.INVISIBLE);
            footerText.setText("more");
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TweetListFragment.this.service = binder.getService();
            serviceBound = true;

            if (mode == MODE_TRACE) {
                AsyncTask<Status, Void, Void> task = new AsyncTask<Status, Void, Void>() {
                    @Override
                    protected Void doInBackground(twitter4j.Status... params) {
                        twitter.setOAuthAccessToken(user.getAccessToken());
                        twitter4j.Status status = params[0];
                        while (status.getInReplyToStatusId() > -1) {
                            try {
                                final twitter4j.Status reply = status = twitter.showStatus(status.getInReplyToStatusId());
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        statuses.add(reply);
                                        adapterWrap.notifyDataSetChanged();
                                    }
                                });
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
            else {
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
                            }
                            lastStatusId = responseList.get(responseList.size() - 1).getId();
                            return responseList;
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(ResponseList<twitter4j.Status> statuses) {
                        if (statuses != null) {
                            TweetListFragment.this.statuses.addAll(statuses);
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
    public void onStatus(AuthUserRecord from, Status status) {
        if (user == null || user.equals(from)) {
            statuses.addFirst(status);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    adapterWrap.notifyDataSetChanged();
                    if (statuses.size() == 1 || listView.getFirstVisiblePosition() < 2) {
                        listView.setSelection(0);
                    }
                    else {
                        listView.setSelection(listView.getFirstVisiblePosition() + 1);
                    }
                }
            });
        }
    }

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {

    }

}
