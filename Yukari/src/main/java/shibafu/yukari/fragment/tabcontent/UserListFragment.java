package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import butterknife.BindView;
import butterknife.ButterKnife;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.fragment.UserListEditDialogFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;

import java.util.Iterator;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class UserListFragment extends TwitterListFragment<UserList> implements SimpleAlertDialogFragment.OnDialogChoseListener{
    public static final int MODE_FOLLOWING = 0;
    public static final int MODE_MEMBERSHIP = 1;

    private static final int REQUEST_D_CREATE = 1;
    private static final int REQUEST_D_EDIT = 2;
    private static final int REQUEST_D_DELETE = 3;

    private static final int REQUEST_SUBSCRIBE = 4;
    private static final int REQUEST_UNSUBSCRIBE = 5;

    private User targetUser = null;

    private UserListAdapter adapter;
    private long loadCursor = -1;
    private MenuItem addMenu;

    private UserList preDelete;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
        setHasOptionsMenu(true);
        disableReload();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new UserListAdapter(getActivity(), elements);
        setListAdapter(adapter);

        registerForContextMenu(getListView());
    }

    @Override
    public boolean onListItemClick(int position, UserList clickedElement) {
        DefaultTweetListFragment fragment = new DefaultTweetListFragment();
        Bundle args = new Bundle();
        args.putInt(FriendListFragment.EXTRA_MODE, TabType.TABTYPE_LIST);
        args.putSerializable(TweetListFragment.EXTRA_USER, getCurrentUser());
        args.putString(TweetListFragment.EXTRA_TITLE, String.format("List: @%s/%s", clickedElement.getUser().getScreenName(), clickedElement.getName()));
        args.putLong(DefaultTweetListFragment.EXTRA_LIST_ID, clickedElement.getId());
        fragment.setArguments(args);
        if (getActivity() instanceof ProfileActivity) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, "contain");
            transaction.addToBackStack(null);
            transaction.commit();
        }
        return true;
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
                UserListEditDialogFragment fragment = UserListEditDialogFragment.newInstance(getCurrentUser(), REQUEST_D_CREATE);
                fragment.setTargetFragment(this, 1);
                fragment.show(getChildFragmentManager(), "new");
                return true;
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
            } else {
                menu.findItem(R.id.action_unsubscribe).setVisible(true);
                menu.findItem(R.id.action_subscribe).setVisible(true);
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
            case R.id.action_subscribe:
            {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, String.valueOf(userList.getId()));
                startActivityForResult(intent, REQUEST_SUBSCRIBE);
                return true;
            }
            case R.id.action_unsubscribe:
            {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, String.valueOf(userList.getId()));
                startActivityForResult(intent, REQUEST_UNSUBSCRIBE);
                return true;
            }
            case R.id.action_edit:
            {
                UserListEditDialogFragment fragment = UserListEditDialogFragment.newInstance(getCurrentUser(), userList, REQUEST_D_EDIT);
                fragment.setTargetFragment(this, 1);
                fragment.show(getChildFragmentManager(), "edit");
                return true;
            }
            case R.id.action_delete:
            {
                preDelete = userList;
                SimpleAlertDialogFragment fragment = SimpleAlertDialogFragment.newInstance(
                        REQUEST_D_DELETE,
                        "確認",
                        "リストを削除してもよろしいですか？",
                        "OK",
                        "キャンセル"
                );
                fragment.setTargetFragment(this, 1);
                fragment.show(getChildFragmentManager(), "delete");
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_SUBSCRIBE: {
                    new ThrowableTwitterAsyncTask<Long, Long>(this) {

                        @Override
                        protected void showToast(String message) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        protected ThrowableResult<Long> doInBackground(Long... params) {
                            Twitter twitter = getTwitterInstance(getCurrentUser());
                            try {
                                twitter.createUserListSubscription(params[0]);
                                return new ThrowableResult<>(params[0]);
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return new ThrowableResult<>(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(ThrowableResult<Long> result) {
                            super.onPostExecute(result);
                            if (!result.isException()) {
                                Toast.makeText(getActivity(), "リストを保存しました", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.executeParallel(Long.valueOf(data.getStringExtra(AccountChooserActivity.EXTRA_METADATA)));
                    break;
                }
                case REQUEST_UNSUBSCRIBE: {
                    new ThrowableTwitterAsyncTask<Long, Long>(this) {

                        @Override
                        protected void showToast(String message) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        protected ThrowableResult<Long> doInBackground(Long... params) {
                            Twitter twitter = getTwitterInstance(getCurrentUser());
                            try {
                                twitter.destroyUserListSubscription(params[0]);
                                return new ThrowableResult<>(params[0]);
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return new ThrowableResult<>(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(ThrowableResult<Long> result) {
                            super.onPostExecute(result);
                            if (!result.isException()) {
                                Toast.makeText(getActivity(), "リストの保存を解除しました", Toast.LENGTH_SHORT).show();
                                if (targetUser.getId() == data.getLongExtra(AccountChooserActivity.EXTRA_SELECTED_USERID, -1)
                                        && getMode() == MODE_FOLLOWING) {
                                    for (Iterator<UserList> iterator = elements.iterator(); iterator.hasNext(); ) {
                                        if (iterator.next().getId() == result.getResult()) {
                                            iterator.remove();
                                            adapter.notifyDataSetChanged();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }.executeParallel(Long.valueOf(data.getStringExtra(AccountChooserActivity.EXTRA_METADATA)));
                    break;
                }
            }
        }
    }

    @Override
    public void onDialogChose(final int requestCode, int which, Bundle extras) {
        if (requestCode == REQUEST_D_DELETE && which == DialogInterface.BUTTON_POSITIVE) {
            new ThrowableTwitterAsyncTask<Long, Void>(this) {

                @Override
                protected void showToast(String message) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }

                @Override
                protected ThrowableResult<Void> doInBackground(Long... params) {
                    Twitter twitter = getTwitterInstance(getCurrentUser());
                    try {
                        twitter.destroyUserList(params[0]);
                        return new ThrowableResult<>((Void)null);
                    } catch (TwitterException e) {
                        e.printStackTrace();
                        return new ThrowableResult<>(e);
                    }
                }

                @Override
                protected void onPostExecute(ThrowableResult<Void> result) {
                    super.onPostExecute(result);
                    if (!result.isException()) {
                        Toast.makeText(getActivity(), "削除しました", Toast.LENGTH_SHORT).show();
                        elements.clear();
                        adapter.notifyDataSetChanged();
                        executeLoader(LOADER_LOAD_INIT, getCurrentUser());
                    }
                }
            }.executeParallel(preDelete.getId());
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            elements.clear();
            adapter.notifyDataSetChanged();
            executeLoader(LOADER_LOAD_INIT, getCurrentUser());
        }
    }

    private class ListLoadTask extends AsyncTask<Void, Void, ResponseList<UserList>> {

        @Override
        protected ResponseList<UserList> doInBackground(Void... params) {
            try {
                Twitter twitter = getTwitterService().getTwitterOrThrow(getCurrentUser());
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
                vh.title.setText(String.format("@%s/%s", list.getUser().getScreenName(), list.getName()));
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
            @BindView(R.id.list_icon) ImageView icon;
            @BindView(R.id.list_name) TextView title;
            @BindView(R.id.list_desc) TextView description;
            @BindView(R.id.list_members) TextView members;

            private ViewHolder(View v) {
                ButterKnife.bind(this, v);
            }
        }
    }

}
