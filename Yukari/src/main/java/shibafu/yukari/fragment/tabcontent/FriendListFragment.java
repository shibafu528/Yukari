package shibafu.yukari.fragment.tabcontent;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class FriendListFragment extends TwitterListFragment<User> {

    public static final String EXTRA_SHOW_USER = "show_user";
    public static final String EXTRA_SEARCH_QUERY = "query";
    public static final String EXTRA_TARGET_LIST_ID = "listid";

    public static final int MODE_FRIEND          = 0;
    public static final int MODE_FOLLOWER        = 1;
    public static final int MODE_BLOCKING        = 2;
    public static final int MODE_SEARCH          = 3;
    public static final int MODE_LIST_MEMBER     = 4;
    public static final int MODE_LIST_SUBSCRIBER = 5;

    private User targetUser = null;
    private long targetListId;
    private String query;

    private UserAdapter adapter;
    private long loadCursor = -1;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        query = args.getString(EXTRA_SEARCH_QUERY);
        targetListId = args.getLong(EXTRA_TARGET_LIST_ID);
        disableReload();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new UserAdapter(getActivity(), elements);
        setListAdapter(adapter);
    }

    @Override
    public boolean isCloseable() {
        return false;
    }

    @Override
    public boolean onListItemClick(int position, User clickedElement) {
        Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER, getCurrentUser());
        intent.putExtra(ProfileActivity.EXTRA_TARGET, clickedElement.getId());
        startActivity(intent);
        return true;
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        switch (requestMode) {
            case LOADER_LOAD_INIT:
                loadCursor = getMode() != MODE_SEARCH ? -1 : 1;
            default:
                new FriendsLoadTask().execute();
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

    private class FriendsLoadTask extends AsyncTask<Void, Void, ResponseList<User>> {

        @Override
        protected ResponseList<User> doInBackground(Void... params) {
            try {
                Twitter twitter = getTwitterService().getTwitterOrThrow(getCurrentUser());
                ResponseList<twitter4j.User> responseList = null;
                switch (getMode()) {
                    case MODE_FRIEND:
                        responseList = twitter.getFriendsList(targetUser.getId(), loadCursor);
                        break;
                    case MODE_FOLLOWER:
                        responseList = twitter.getFollowersList(targetUser.getId(), loadCursor);
                        break;
                    case MODE_BLOCKING:
                        responseList = twitter.getBlocksList(loadCursor);
                        break;
                    case MODE_SEARCH:
                        responseList = twitter.searchUsers(query, (int) loadCursor);
                        break;
                    case MODE_LIST_MEMBER:
                        responseList = twitter.getUserListMembers(targetListId, loadCursor);
                        break;
                    case MODE_LIST_SUBSCRIBER:
                        responseList = twitter.getUserListSubscribers(targetListId, loadCursor);
                        break;
                }
                if (responseList != null && !responseList.isEmpty()) {
                    if (getMode() == MODE_SEARCH) {
                        loadCursor++;
                        if (responseList.size() < 20) {
                            loadCursor = 0;
                        }
                    }
                    else {
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
        protected void onPostExecute(ResponseList<twitter4j.User> users) {
            if (users != null) {
                elements.addAll(users);
                adapter.notifyDataSetChanged();
            }
            changeFooterProgress(false);

            if (loadCursor == 0) {
                removeFooter();
            }
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
                vh.ivIcon = (ImageView) v.findViewById(R.id.user_icon);
                vh.tvName = (TextView) v.findViewById(R.id.user_name);
                vh.tvName.setTypeface(FontAsset.getInstance(getContext()).getFont());
                vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                vh.tvScreenName.setTypeface(FontAsset.getInstance(getContext()).getFont());
                v.setTag(vh);
            }
            else {
                vh = (ViewHolder) v.getTag();
            }

            User u = getItem(position);
            if (u != null) {
                vh.tvName.setText(u.getName());
                vh.tvScreenName.setText("@" + u.getScreenName());
                ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), vh.ivIcon, u.getBiggerProfileImageURLHttps());
            }

            return v;
        }

        private class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }

}
