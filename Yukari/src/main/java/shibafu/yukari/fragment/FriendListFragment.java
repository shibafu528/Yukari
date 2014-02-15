package shibafu.yukari.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.util.LinkedList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.IconLoaderTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/01.
 */
public class FriendListFragment extends AttachableListFragment {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_SHOW_USER = "show_user";

    public static final int MODE_FRIEND   = 0;
    public static final int MODE_FOLLOWER = 1;

    private LinkedList<User> users = new LinkedList<User>();

    private Twitter twitter;
    private AuthUserRecord user;
    private String title;
    private int mode;

    private User targetUser = null;

    private ListView listView;
    private UserAdapter adapter;
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;

    private long loadCursor = -1;
    private boolean isLoading = false;

    private TwitterService service;
    private boolean serviceBound = false;
    private Handler handler = new Handler();

    public FriendListFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        title = args.getString(EXTRA_TITLE);
        mode = args.getInt(EXTRA_MODE);
        targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
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

        adapter = new UserAdapter(getActivity().getApplicationContext(), users);
        setListAdapter(adapter);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < users.size()) {
                    User u = users.get(position);
                    Intent intent = new Intent(getActivity(), ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER, user);
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, u.getId());
                    startActivity(intent);
                }
                else if (position == users.size() && !isLoading) {
                    new FriendsLoadTask().execute();
                    changeFooterProgress(true);
                }
            }
        });

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
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
        listView.setSelection(users.size() - 1);
    }

    @Override
    public boolean isCloseable() {
        return false;
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            FriendListFragment.this.service = binder.getService();
            twitter = FriendListFragment.this.service.getTwitter();
            serviceBound = true;

            if (users.size() > 0) return;

            if (user == null) {
                user = FriendListFragment.this.service.getPrimaryUser();
            }
            new FriendsLoadTask().execute();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class FriendsLoadTask extends AsyncTask<Void, Void, PagableResponseList<User>> {

        @Override
        protected PagableResponseList<twitter4j.User> doInBackground(Void... params) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                PagableResponseList<twitter4j.User> responseList = null;
                switch (mode) {
                    case MODE_FRIEND:
                        responseList = twitter.getFriendsList(targetUser.getId(), loadCursor);
                        break;
                    case MODE_FOLLOWER:
                        responseList = twitter.getFollowersList(targetUser.getId(), loadCursor);
                        break;
                }
                if (responseList != null && !responseList.isEmpty()) {
                    loadCursor = responseList.getNextCursor();
                }
                return responseList;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(PagableResponseList<twitter4j.User> users) {
            if (users != null) {
                FriendListFragment.this.users.addAll(users);
                adapter.notifyDataSetChanged();
            }
            changeFooterProgress(false);
        }
    }

    private class UserAdapter extends ArrayAdapter<User> {

        private LayoutInflater inflater;

        public UserAdapter(Context context, List<User> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder vh;

            if (v == null) {
                v = inflater.inflate(R.layout.row_user, parent, false);
                vh = new ViewHolder();
                vh.ivIcon = (SmartImageView) v.findViewById(R.id.user_icon);
                vh.tvName = (TextView) v.findViewById(R.id.user_name);
                vh.tvName.setTypeface(FontAsset.getInstance(getContext()).getFont());
                vh.tvName.setTextColor(getResources().getColor(android.R.color.primary_text_light));
                vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                vh.tvScreenName.setTypeface(FontAsset.getInstance(getContext()).getFont());
                vh.tvScreenName.setTextColor(getResources().getColor(android.R.color.primary_text_light));
                v.setTag(vh);
            }
            else {
                vh = (ViewHolder) v.getTag();
            }

            User u = getItem(position);
            if (u != null) {
                vh.tvName.setText(u.getName());
                vh.tvScreenName.setText("@" + u.getScreenName());
                vh.ivIcon.setImageResource(R.drawable.yukatterload);
                vh.ivIcon.setTag(u.getBiggerProfileImageURL());
                IconLoaderTask loaderTask = new IconLoaderTask(getActivity(), vh.ivIcon);
                loaderTask.executeIf(u.getBiggerProfileImageURL());
            }

            return v;
        }

        private class ViewHolder {
            SmartImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }

}
