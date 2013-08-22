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

import java.util.Date;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

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

    private LinkedList<Status> statuses = new LinkedList<Status>();

    private Twitter twitter;
    private TweetAdapterWrap adapterWrap;
    private AuthUserRecord user;
    private String title;
    private int mode;

    private ListView listView;

    private TwitterService service;
    private boolean serviceBound = false;
    private Handler handler = new Handler();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        mode = args.getInt(EXTRA_MODE, MODE_EMPTY);
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        twitter = TwitterUtil.getTwitterInstance(getActivity());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(getActivity().getApplicationContext(), user, statuses);
        setListAdapter(adapterWrap.getAdapter());
        listView = getListView();
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TweetListFragment.this.service = binder.getService();
            serviceBound = true;

            AsyncTask<Void, Void, ResponseList<Status>> task = new AsyncTask<Void, Void, ResponseList<Status>>() {
                @Override
                protected ResponseList<twitter4j.Status> doInBackground(Void... params) {
                    twitter.setOAuthAccessToken(user.getAccessToken());
                    try {
                        ResponseList<twitter4j.Status> homeTimeline = twitter.getHomeTimeline();
                        return homeTimeline;
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
                    TweetListFragment.this.service.addStatusListener(TweetListFragment.this);
                }
            };
            task.execute();
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
