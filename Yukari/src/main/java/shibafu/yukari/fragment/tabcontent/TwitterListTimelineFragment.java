package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.fragment.UserListEditDialogFragment;
import shibafu.yukari.fragment.base.TwitterFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserList;

/**
 * Twitterリストに関するメニューコマンドを備えた、プロフィール内でのリスト表示専用Fragment
 */
public class TwitterListTimelineFragment extends TwitterFragment implements SimpleAlertDialogFragment.OnDialogChoseListener {
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LIST_ID = "listId";

    private static final int REQUEST_D_EDIT = 1;
    private static final int REQUEST_D_DELETE = 2;

    private static final int REQUEST_SUBSCRIBE = 4;
    private static final int REQUEST_UNSUBSCRIBE = 5;

    private AuthUserRecord user;
    private String title;
    private long listId;

    private UserList targetList;

    private MenuItem miEditList;
    private MenuItem miDeleteList;
    private MenuItem miSubscribeList;
    private MenuItem miUnsubscriveList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        title = args.getString(EXTRA_TITLE);
        listId = args.getLong(EXTRA_LIST_ID);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_parent, container, false);

        if (savedInstanceState == null) {
            StringBuilder query = new StringBuilder();
            query.append("from list:\"")
                    .append(user.ScreenName)
                    .append("/")
                    .append(listId)
                    .append("\"");

            TimelineFragment fragment = new TimelineFragment();
            Bundle childArgs = new Bundle();
            childArgs.putSerializable(TweetListFragment.EXTRA_USER, user);
            childArgs.putString(TweetListFragment.EXTRA_TITLE, title);
            childArgs.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_LIST);
            childArgs.putString(TimelineFragment.EXTRA_FILTER_QUERY, query.toString());
            fragment.setArguments(childArgs);

            getChildFragmentManager().beginTransaction().replace(R.id.frame, fragment, "timeline").commit();
        }

        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.list, menu);
        miEditList = menu.findItem(R.id.action_edit);
        miDeleteList = menu.findItem(R.id.action_delete);
        miSubscribeList = menu.findItem(R.id.action_subscribe);
        miUnsubscriveList = menu.findItem(R.id.action_unsubscribe);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_member:
            {
                FriendListFragment fragment = new FriendListFragment();
                Bundle args = new Bundle();
                args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_LIST_MEMBER);
                args.putSerializable(TweetListFragment.EXTRA_USER, user);
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, listId);
                args.putString(TweetListFragment.EXTRA_TITLE, "Member: " + title.replace("List: ", ""));
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
                args.putSerializable(TweetListFragment.EXTRA_USER, user);
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, listId);
                args.putString(TweetListFragment.EXTRA_TITLE, "Subscriber: " + title.replace("List: ", ""));
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
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, String.valueOf(listId));
                startActivityForResult(intent, REQUEST_SUBSCRIBE);
                return true;
            }
            case R.id.action_unsubscribe:
            {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, String.valueOf(listId));
                startActivityForResult(intent, REQUEST_UNSUBSCRIBE);
                return true;
            }
            case R.id.action_edit:
            {
                if (targetList != null) {
                    UserListEditDialogFragment fragment = UserListEditDialogFragment.newInstance(user, targetList, REQUEST_D_EDIT);
                    fragment.setTargetFragment(this, 1);
                    fragment.show(getFragmentManager(), "edit");
                }
                return true;
            }
            case R.id.action_delete:
            {
                SimpleAlertDialogFragment fragment = SimpleAlertDialogFragment.newInstance(
                        REQUEST_D_DELETE,
                        "確認",
                        "リストを削除してもよろしいですか？",
                        "OK",
                        "キャンセル"
                );
                fragment.setTargetFragment(this, 1);
                fragment.show(getFragmentManager(), "delete");
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        } else {
            activity.setTitle(title);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_SUBSCRIBE: {
                    new ThrowableTwitterAsyncTask<Long, Boolean>(this) {

                        @Override
                        protected void showToast(String message) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        protected ThrowableResult<Boolean> doInBackground(Long... params) {
                            Twitter twitter = getTwitterInstance(user);
                            try {
                                twitter.createUserListSubscription(params[0]);
                                return new ThrowableResult<>(true);
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return new ThrowableResult<>(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(ThrowableResult<Boolean> result) {
                            super.onPostExecute(result);
                            if (!result.isException()) {
                                Toast.makeText(getActivity(), "リストを保存しました", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.executeParallel(Long.valueOf(data.getStringExtra(AccountChooserActivity.EXTRA_METADATA)));
                    break;
                }
                case REQUEST_UNSUBSCRIBE: {
                    new ThrowableTwitterAsyncTask<Long, Boolean>(this) {

                        @Override
                        protected void showToast(String message) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        protected ThrowableResult<Boolean> doInBackground(Long... params) {
                            Twitter twitter = getTwitterInstance(user);
                            try {
                                twitter.destroyUserListSubscription(params[0]);
                                return new ThrowableResult<>(true);
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return new ThrowableResult<>(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(ThrowableResult<Boolean> result) {
                            super.onPostExecute(result);
                            if (!result.isException()) {
                                Toast.makeText(getActivity(), "リストの保存を解除しました", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.executeParallel(Long.valueOf(data.getStringExtra(AccountChooserActivity.EXTRA_METADATA)));
                    break;
                }
            }
        }
    }

    @Override
    public void onServiceConnected() {
        new ThrowableTwitterAsyncTask<Void, Boolean>(this) {
            @Override
            protected void showToast(String message) {}

            @Override
            protected ThrowableResult<Boolean> doInBackground(Void... params) {
                Twitter twitter = getTwitterInstance(user);
                try {
                    targetList = twitter.showUserList(listId);
                    return new ThrowableResult<>(true);
                } catch (TwitterException e) {
                    e.printStackTrace();
                    return new ThrowableResult<>(e);
                }
            }

            @Override
            protected void onPostExecute(ThrowableResult<Boolean> result) {
                super.onPostExecute(result);
                if (!result.isException()) {
                    if (targetList.getUser().getId() == user.NumericId) {
                        miEditList.setVisible(true);
                        miDeleteList.setVisible(true);
                    } else {
                        miUnsubscriveList.setVisible(true);
                        miSubscribeList.setVisible(true);
                    }
                }
            }
        }.executeParallel();
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public void onDialogChose(int requestCode, int which, Bundle extras) {
        if (requestCode == REQUEST_D_DELETE && which == DialogInterface.BUTTON_POSITIVE) {
            new ThrowableTwitterAsyncTask<Long, Void>(this) {

                @Override
                protected void showToast(String message) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }

                @Override
                protected ThrowableResult<Void> doInBackground(Long... params) {
                    Twitter twitter = getTwitterInstance(user);
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
                        if (getActivity() instanceof ProfileActivity) {
                            if (getFragmentManager().getBackStackEntryCount() > 0) {
                                getFragmentManager().popBackStackImmediate();
                            } else {
                                getActivity().finish();
                            }
                        }
                    }
                }
            }.executeParallel(listId);
        }
    }
}
