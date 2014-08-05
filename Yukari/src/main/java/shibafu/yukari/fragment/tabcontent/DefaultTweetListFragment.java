package shibafu.yukari.fragment.tabcontent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.LongSparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;

import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.fragment.UserListEditDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;

/**
 * Created by shibafu on 14/02/13.
 */
public class DefaultTweetListFragment extends TweetListFragment implements StatusManager.StatusListener, SimpleAlertDialogFragment.OnDialogChoseListener {

    public static final String EXTRA_LIST_ID = "listid";
    public static final String EXTRA_TRACE_START = "trace_start";

    private static final int REQUEST_D_EDIT = 1;
    private static final int REQUEST_D_DELETE = 2;

    private Status traceStart = null;
    private User targetUser = null;
    private long listId = -1;

    private UserList targetList;
    private MenuItem miEditList;
    private MenuItem miDeleteList;
    private MenuItem miSubscribeList;
    private MenuItem miUnsubscriveList;

    private LongSparseArray<Long> lastStatusIds = new LongSparseArray<>();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();

        int mode = getMode();
        if (mode == TabType.TABTYPE_TRACE) {
            Object trace = args.getSerializable(EXTRA_TRACE_START);
            if (trace instanceof PreformedStatus) {
                traceStart = (Status) trace;
                if (elements.isEmpty()) {
                    elements.add((PreformedStatus) trace);
                }
            }
            else if (trace instanceof Status) {
                traceStart = (Status) trace;
                if (elements.isEmpty()) {
                    elements.add(new PreformedStatus(traceStart, getCurrentUser()));
                }
            }
        }
        else {
            if (mode == TabType.TABTYPE_USER || mode == TabType.TABTYPE_FAVORITE) {
                targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
            }
            else if (mode == TabType.TABTYPE_LIST) {
                listId = args.getLong(EXTRA_LIST_ID, -1);
                setHasOptionsMenu(true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getMode() == TabType.TABTYPE_HOME) {
            getActivity().registerReceiver(onReloadReceiver, new IntentFilter(TwitterService.RELOADED_USERS));
            getActivity().registerReceiver(onActiveChangedReceiver, new IntentFilter(TwitterService.CHANGED_ACTIVE_STATE));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getMode() == TabType.TABTYPE_HOME) {
            getActivity().unregisterReceiver(onReloadReceiver);
            getActivity().unregisterReceiver(onActiveChangedReceiver);
        }
    }

    @Override
    public void onDetach() {
        if (isServiceBound()) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDetach();
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        DefaultRESTLoader loader = new DefaultRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
                loader.execute(loader.new Params(userRecord));
                break;
            case LOADER_LOAD_MORE:
                loader.execute(loader.new Params(lastStatusIds.get(userRecord.NumericId, -1L), userRecord));
                break;
            case LOADER_LOAD_UPDATE:
                loader.execute(loader.new Params(userRecord, true));
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        if (getMode() == TabType.TABTYPE_TRACE) {
            AsyncTask<Status, Void, Void> task = new AsyncTask<Status, Void, Void>() {
                @Override
                protected Void doInBackground(twitter4j.Status... params) {
                    twitter.setOAuthAccessToken(getCurrentUser().getAccessToken());
                    twitter4j.Status status = params[0];
                    while (status.getInReplyToStatusId() > -1) {
                        try {
                            final twitter4j.Status reply = status = twitter.showStatus(status.getInReplyToStatusId());
                            final PreformedStatus ps = new PreformedStatus(reply, getCurrentUser());
                            final int location = prepareInsertStatus(ps);
                            if (location > -1) {
                                getHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        elements.add(location, ps);
                                        adapterWrap.notifyDataSetChanged();
                                    }
                                });
                            }
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    removeFooter();
                }
            };
            task.execute(traceStart);
            changeFooterProgress(true);
        } else if (elements.isEmpty()) {
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_INIT, user);
            }
        }

        if (getMode() == TabType.TABTYPE_LIST && !(getActivity() instanceof MainActivity)) {
            new ThrowableTwitterAsyncTask<Void, Boolean>(this) {
                @Override
                protected void showToast(String message) {}

                @Override
                protected ThrowableResult<Boolean> doInBackground(Void... params) {
                    Twitter twitter = getTwitterInstance(getCurrentUser());
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
                        if (targetList.getUser().getId() == getCurrentUser().NumericId) {
                            miEditList.setVisible(true);
                            miDeleteList.setVisible(true);
                        } else if (targetList.isFollowing()) {
                            miUnsubscriveList.setVisible(true);
                        } else {
                            miSubscribeList.setVisible(true);
                        }
                    }
                }
            }.executeParallel();
        }

        getStatusManager().addStatusListener(this);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public boolean isCloseable() {
        return false;
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
                args.putSerializable(TweetListFragment.EXTRA_USER, getCurrentUser());
                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, listId);
                args.putString(TweetListFragment.EXTRA_TITLE, "Member: " + getTitle().replace("List: ", ""));
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
                args.putLong(FriendListFragment.EXTRA_TARGET_LIST_ID, listId);
                args.putString(TweetListFragment.EXTRA_TITLE, "Subscriber: " + getTitle().replace("List: ", ""));
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
                new ThrowableTwitterAsyncTask<Long, Boolean>(this) {

                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected ThrowableResult<Boolean> doInBackground(Long... params) {
                        Twitter twitter = getTwitterInstance(getCurrentUser());
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
                            miSubscribeList.setVisible(false);
                            miUnsubscriveList.setVisible(true);
                        }
                    }
                }.executeParallel(listId);
                return true;
            }
            case R.id.action_unsubscribe:
            {
                new ThrowableTwitterAsyncTask<Long, Boolean>(this) {

                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected ThrowableResult<Boolean> doInBackground(Long... params) {
                        Twitter twitter = getTwitterInstance(getCurrentUser());
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
                            miSubscribeList.setVisible(true);
                            miUnsubscriveList.setVisible(false);
                        }
                    }
                }.executeParallel(listId);
                return true;
            }
            case R.id.action_edit:
            {
                if (targetList != null) {
                    UserListEditDialogFragment fragment = UserListEditDialogFragment.newInstance(getCurrentUser(), targetList, REQUEST_D_EDIT);
                    fragment.setTargetFragment(this, 1);
                    fragment.show(getChildFragmentManager(), "edit");
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
                fragment.show(getChildFragmentManager(), "delete");
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDialogChose(int requestCode, int which) {
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


    private BroadcastReceiver onReloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };

    private BroadcastReceiver onActiveChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<AuthUserRecord> inactiveList = (ArrayList<AuthUserRecord>) intent.getSerializableExtra(TwitterService.EXTRA_CHANGED_INACTIVE);
            for (AuthUserRecord inactive : inactiveList) {
                if (users.contains(inactive)) {
                    users.remove(inactive);
                }
            }
        }
    };

    @Override
    public String getStreamFilter() {
        return null;
    }

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status, boolean muted) {
        if ((getMode() == TabType.TABTYPE_HOME || getMode() == TabType.TABTYPE_MENTION)
                && users.contains(from) && !elements.contains(status)) {
            if (getMode() == TabType.TABTYPE_MENTION &&
                    (!status.isMentionedToMe() || status.isRetweet())) return;

            if (muted) {
                stash.add(status);
            } else {
                final int position = prepareInsertStatus(status);
                if (position > -1) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (!elements.contains(status)) {
                                if (position < elements.size()) {
                                    if (elements.get(position).getId() == status.getId())
                                        return;
                                }
                                elements.add(position, status);
                                adapterWrap.notifyDataSetChanged();
                                if (elements.size() == 1 || listView.getFirstVisiblePosition() < 2) {
                                    listView.setSelection(0);
                                } else {
                                    listView.setSelection(listView.getFirstVisiblePosition() + 1);
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {}

    @Override
    public void onUpdatedStatus(final AuthUserRecord from, int kind, final Status status) {
        switch (kind) {
            case StatusManager.UPDATE_DELETED:
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        Iterator<PreformedStatus> iterator = elements.iterator();
                        while (iterator.hasNext()) {
                            if (iterator.next().getId() == status.getId()) {
                                iterator.remove();
                                adapterWrap.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                });
                for (Iterator<PreformedStatus> iterator = stash.iterator(); iterator.hasNext(); ) {
                    if (iterator.next().getId() == status.getId()) {
                        iterator.remove();
                    }
                }
                break;
            default:
                int position = 0;
                for (; position < elements.size(); ++position) {
                    if (elements.get(position).getId() == status.getId()) break;
                }
                if (position < elements.size()) {
                    final int p = position;
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            elements.get(p).merge(status, from);
                            adapterWrap.notifyDataSetChanged();
                        }
                    });
                }
                else {
                    for (position = 0; position < stash.size(); ++position) {
                        if (stash.get(position).getId() == status.getId()) break;
                    }
                    if (position < stash.size()) {
                        stash.get(position).merge(status, from);
                    }
                }
        }
    }

    private class DefaultRESTLoader
            extends RESTLoader<DefaultRESTLoader.Params, PreformedResponseList<PreformedStatus>> {
        class Params {
            private Paging paging;
            private AuthUserRecord userRecord;
            private boolean saveLastPaging;

            public Params(AuthUserRecord userRecord) {
                this.paging = new Paging();
                this.userRecord = userRecord;
            }

            public Params(long lastStatusId, AuthUserRecord userRecord) {
                this.paging = new Paging();
                if (lastStatusId > -1) {
                    paging.setMaxId(lastStatusId - 1);
                }
                this.userRecord = userRecord;
            }

            public Params(AuthUserRecord userRecord, boolean saveLastPaging) {
                this.paging = new Paging();
                this.userRecord = userRecord;
                this.saveLastPaging = saveLastPaging;
            }

            public Paging getPaging() {
                return paging;
            }

            public AuthUserRecord getUserRecord() {
                return userRecord;
            }

            public boolean isSaveLastPaging() {
                return saveLastPaging;
            }
        }

        protected DefaultRESTLoader(RESTLoaderInterface loaderInterface) {
            super(loaderInterface);
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
            try {
                ResponseList<twitter4j.Status> responseList = null;
                Paging paging = params[0].getPaging();
                paging.setCount(60);
                switch (getMode()) {
                    case TabType.TABTYPE_HOME:
                        responseList = twitter.getHomeTimeline(paging);
                        break;
                    case TabType.TABTYPE_MENTION:
                        responseList = twitter.getMentionsTimeline(paging);
                        break;
                    case TabType.TABTYPE_USER:
                        responseList = twitter.getUserTimeline(targetUser.getId(), paging);
                        break;
                    case TabType.TABTYPE_FAVORITE:
                        responseList = twitter.getFavorites(targetUser.getId(), paging);
                        break;
                    case TabType.TABTYPE_LIST:
                        responseList = twitter.getUserListStatuses(listId, paging);
                        break;
                }
                if (!params[0].isSaveLastPaging()) {
                    if (responseList == null) {
                        lastStatusIds.put(params[0].getUserRecord().NumericId, -1L);
                    }
                    else if (responseList.size() > 0) {
                        lastStatusIds.put(params[0].getUserRecord().NumericId,
                                responseList.get(responseList.size() - 1).getId());
                    }
                }
                return PRListFactory.create(responseList, params[0].getUserRecord());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(PreformedResponseList<PreformedStatus> result) {
            super.onPostExecute(result);
            setRefreshComplete();
        }
    }
}
