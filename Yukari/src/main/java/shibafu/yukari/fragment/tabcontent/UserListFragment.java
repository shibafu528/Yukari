package shibafu.yukari.fragment.tabcontent;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
    private MenuItem addMenu;

    public UserListFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new UserListAdapter(getActivity().getApplicationContext(), elements);
        setListAdapter(adapter);

        registerForContextMenu(getListView());
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
        if (getMode() == MODE_FOLLOWING && targetUser.getId() == getCurrentUser().NumericId && addMenu != null) {
            addMenu.setVisible(true);
        }
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "新規作成")
                .setIcon(R.drawable.ic_action_add)
                .setVisible(false);
        MenuItemCompat.setShowAsAction(addMenu, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                Toast.makeText(getActivity(), "a", Toast.LENGTH_SHORT).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo adapterinfo = (AdapterView.AdapterContextMenuInfo)menuInfo;
        ListView listView = (ListView)v;
        UserList userList = (UserList) listView.getItemAtPosition(adapterinfo.position);

        if (userList != null) {
            getActivity().getMenuInflater().inflate(R.menu.list, menu);
            menu.setHeaderTitle(userList.getFullName());

            if (userList.getUser().getId() == getCurrentUser().NumericId) {
                menu.findItem(R.id.action_edit).setVisible(true);
                menu.findItem(R.id.action_delete).setVisible(true);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        UserList userList = (UserList) listView.getItemAtPosition(info.position);
        switch (item.getItemId()) {
            case R.id.action_show_member:
            {
                FriendListFragment fragment = new FriendListFragment();
                Bundle args = new Bundle();
                args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_LIST_MEMBER);
                args.putSerializable(TweetListFragment.EXTRA_USER, getCurrentUser());
                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, userList.getId());
                args.putString(TweetListFragment.EXTRA_TITLE, "Member: " + userList.getFullName());
                fragment.setArguments(args);
                if (getActivity() instanceof ProfileActivity) {
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.frame, fragment, "contain");
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
                return true;
            }
            case R.id.action_show_subscriber:
            {
                FriendListFragment fragment = new FriendListFragment();
                Bundle args = new Bundle();
                args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_LIST_SUBSCRIBER);
                args.putSerializable(TweetListFragment.EXTRA_USER, getCurrentUser());
                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, userList.getId());
                args.putString(TweetListFragment.EXTRA_TITLE, "Subscriber: " + userList.getFullName());
                fragment.setArguments(args);
                if (getActivity() instanceof ProfileActivity) {
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.frame, fragment, "contain");
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
                return true;
            }
        }
        return super.onContextItemSelected(item);
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
