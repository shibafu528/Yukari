package shibafu.yukari.fragment.tabcontent;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import shibafu.yukari.R;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;

/**
 * Created by Shibafu on 13/08/01.
 */
public class UserListFragment extends TwitterListFragment<UserList> {
    public static final int MODE_FOLLOWING = 0;
    public static final int MODE_MEMBERSHIP = 1;

    private User targetUser = null;

    private UserListAdapter adapter;
    private long loadCursor = -1;

    public UserListFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new UserListAdapter(getActivity().getApplicationContext(), elements);
        setListAdapter(adapter);
    }

    @Override
    public boolean isCloseable() {
        return false;
    }

    @Override
    public void onListItemClick(UserList clickedElement) {
        DefaultTweetListFragment fragment = new DefaultTweetListFragment();
        Bundle args = new Bundle();
        args.putInt(FriendListFragment.EXTRA_MODE, TabType.TABTYPE_LIST);
        args.putSerializable(TweetListFragment.EXTRA_USER, getCurrentUser());
        args.putString(TweetListFragment.EXTRA_TITLE, "List: " + clickedElement.getFullName());
        args.putLong(DefaultTweetListFragment.EXTRA_LIST_ID, clickedElement.getId());
        fragment.setArguments(args);
        if (getActivity() instanceof ProfileActivity) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, "contain");
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        switch (requestMode) {
            case LOADER_LOAD_INIT:
                loadCursor = -1;
            default:
                new ListLoadTask().execute();
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        executeLoader(LOADER_LOAD_INIT, getCurrentUser());
    }

    @Override
    public void onServiceDisconnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        if (loadCursor == -2) {
            removeFooter();
        }
    }

    private class ListLoadTask extends AsyncTask<Void, Void, ResponseList<UserList>> {

        @Override
        protected ResponseList<UserList> doInBackground(Void... params) {
            twitter.setOAuthAccessToken(getCurrentUser().getAccessToken());
            try {
                ResponseList<UserList> responseList = null;
                switch (getMode()) {
                    case MODE_FOLLOWING:
                        responseList = twitter.getUserLists(targetUser.getId());
                        break;
                    case MODE_MEMBERSHIP:
                        responseList = twitter.getUserListMemberships(targetUser.getId(), loadCursor);
                        break;
                }
                if (responseList != null && !responseList.isEmpty()) {
                    if (getMode() == MODE_FOLLOWING) {
                        loadCursor = -2;
                    } else {
                        loadCursor = ((PagableResponseList) responseList).getNextCursor();
                    }
                }
                return responseList;
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
        protected void onPostExecute(ResponseList<UserList> users) {
            if (users != null) {
                elements.addAll(users);
                adapter.notifyDataSetChanged();
            }
            changeFooterProgress(false);
            if (loadCursor == -2) {
                removeFooter();
            }
        }
    }

    class UserListAdapter extends ArrayAdapter<UserList> {

        private LayoutInflater inflater;

        public UserListAdapter(Context context, List<UserList> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.row_list, parent, false);
                vh = new ViewHolder(convertView);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }

            UserList list = getItem(position);
            if (list != null) {
                vh.title.setText(list.getFullName());
                vh.description.setText(list.getDescription());
                String members = getString(R.string.list_members_count, list.getMemberCount());
                if (list.isPublic() && list.getSubscriberCount() > 0) {
                    members += getString(R.string.list_subscribers_count, list.getSubscriberCount());
                }
                vh.members.setText(members);
                ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), vh.icon, list.getUser().getBiggerProfileImageURLHttps());
            }

            return convertView;
        }

        class ViewHolder {
            @InjectView(R.id.list_icon) ImageView icon;
            @InjectView(R.id.list_name) TextView title;
            @InjectView(R.id.list_desc) TextView description;
            @InjectView(R.id.list_members) TextView members;

            private ViewHolder(View v) {
                ButterKnife.inject(this, v);
            }
        }
    }

}
