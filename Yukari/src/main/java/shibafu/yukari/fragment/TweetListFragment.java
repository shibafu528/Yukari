package shibafu.yukari.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TweetReceiverService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetListFragment extends ListFragment implements TweetReceiverService.StatusListener {

    public static final int MODE_HOME = 1;

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";

    private LinkedList<Status> statuses = new LinkedList<Status>();

    private TweetAdapterWrap adapterWrap;
    private AuthUserRecord user;
    private String title;
    private int mode;

    private TweetReceiverService service;
    private boolean serviceBound = false;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        mode = args.getInt(EXTRA_MODE);
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(getActivity(), statuses);
        setListAdapter(adapterWrap.getAdapter());

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

            TweetListFragment.this.service.addStatusListener(TweetListFragment.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public void onStatus(AuthUserRecord from, Status status) {
        if (user == null || user == from) {
            statuses.addFirst(status);
            adapterWrap.notifyDataSetChanged();
        }
    }

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {

    }

}
