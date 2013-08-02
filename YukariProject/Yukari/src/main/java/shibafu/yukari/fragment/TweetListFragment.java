package shibafu.yukari.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import shibafu.yukari.R;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TweetReceiverService;
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
public class TweetListFragment extends ListFragment implements TweetReceiverService.StatusListener {

    public static final int MODE_HOME = 1;

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";

    private LinkedList<Status> statuses = new LinkedList<Status>();

    private Twitter twitter;
    private TweetAdapterWrap adapterWrap;
    private AuthUserRecord user;
    private String title;
    private int mode;

    private TweetReceiverService service;
    private boolean serviceBound = false;
    private Handler handler = new Handler();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        mode = args.getInt(EXTRA_MODE);
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        twitter = TwitterUtil.getTwitterInstance(getActivity());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(getActivity().getApplicationContext(), user, statuses);
        setListAdapter(adapterWrap.getAdapter());
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
            }
        });

        getActivity().bindService(new Intent(getActivity(), TweetReceiverService.class), connection, Context.BIND_AUTO_CREATE);
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TweetReceiverService.TweetReceiverBinder binder = (TweetReceiverService.TweetReceiverBinder) service;
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
                    if (statuses.size() == 1 || getListView().getFirstVisiblePosition() < 2) {
                        getListView().setSelection(0);
                    }
                    else {
                        getListView().setSelection(getListView().getFirstVisiblePosition() + 1);
                    }
                }
            });
        }
    }

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {

    }

}
