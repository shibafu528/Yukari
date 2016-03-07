package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.*;
import android.widget.*;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnItemClick;
import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.*;

import java.util.*;

/**
 * Created by shibafu on 14/07/22.
 */
public class ListRegisterDialogFragment extends DialogFragment {
    @InjectView(R.id.tvMenuTitle) TextView menuTitle;
    @InjectView(R.id.ivMenuAccountIcon) ImageView accountIcon;
    @InjectView(R.id.listView) ListView listView;
    @InjectView(R.id.progressBar) ProgressBar progressBar;

    private static final int REQUEST_CHOOSE = 1;
    private static final String ARG_TARGET_USER = "target";

    private User targetUser;

    private TwitterServiceDelegate delegate;
    private AuthUserRecord currentUser;

    private ResponseList<UserList> userLists;
    private List<Long> membership;

    private ListLoadTask currentListLoadTask;
    private Map<ListUpdateTask, UserList> updateTasks = new HashMap<>();
    private UserListAdapter adapter;

    public static ListRegisterDialogFragment newInstance(User targetUser) {
        ListRegisterDialogFragment fragment = new ListRegisterDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TARGET_USER, targetUser);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (getTargetFragment() != null && getTargetFragment() instanceof TwitterServiceDelegate) {
            delegate = (TwitterServiceDelegate) getTargetFragment();
        } else if (activity instanceof TwitterServiceDelegate) {
            delegate = (TwitterServiceDelegate) activity;
        } else {
            throw new RuntimeException("TwitterServiceDelegate cannot find.");
        }
        currentUser = delegate.getTwitterService().getPrimaryUser();

        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(ARG_TARGET_USER);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);
                break;
            case "dark":
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_dark);
                break;
        }

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_list, null);
        ButterKnife.inject(this, v);

        menuTitle.setText(String.format("@%s のリスト", currentUser.ScreenName));
        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), accountIcon, currentUser.ProfileImageUrl);

        dialog.setContentView(v);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = (int) (0.9f * metrics.widthPixels);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = dialogWidth;
        dialog.getWindow().setAttributes(lp);

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        ButterKnife.reset(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        loadList();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (currentListLoadTask != null) {
            currentListLoadTask.cancel(true);
            currentListLoadTask = null;
        }
        updateTasks.clear();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CHOOSE:
                if (resultCode == Activity.RESULT_OK) {
                    AuthUserRecord newUser = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    if (!currentUser.equals(newUser)) {
                        currentUser = newUser;
                        if (userLists != null) userLists.clear();
                        if (adapter != null) adapter.notifyDataSetChanged();
                        menuTitle.setText(String.format("@%s のリスト", currentUser.ScreenName));
                        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), accountIcon, currentUser.ProfileImageUrl);
                        loadList();
                    }
                }
                break;
        }
    }

    private void loadList() {
        if (currentListLoadTask != null) {
            currentListLoadTask.cancel(true);
        }
        switch (Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_lists_membership_finder", "0"))) {
            default:
                currentListLoadTask = new ListLoadTask(ListMembershipChecker.MEMBERS_FULL_SEARCH);
                break;
            case 1:
                currentListLoadTask = new ListLoadTask(ListMembershipChecker.CHECK_MEMBERS_SHOW);
                break;
            case 2:
                currentListLoadTask = new ListLoadTask(ListMembershipChecker.NO_SEARCH);
                break;
        }
        currentListLoadTask.executeParallel(currentUser);
    }

    @OnClick(R.id.llMenuAccountParent)
    void onClickTitle() {
        startActivityForResult(new Intent(getActivity(), AccountChooserActivity.class), REQUEST_CHOOSE);
    }

    @OnItemClick(R.id.listView)
    void onItemClick(int position) {
        UserList userList = userLists.get(position);
        if (!updateTasks.containsValue(userList)) {
            ListUpdateTask task = new ListUpdateTask();
            updateTasks.put(task, userList);
            task.executeParallel(task.new Params(
                    !membership.contains(userList.getId()) ? ListUpdateTask.ADD : ListUpdateTask.REMOVE,
                    currentUser, userList));
            adapter.notifyDataSetChanged();
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
                convertView = inflater.inflate(R.layout.row_check, null);
                vh = new ViewHolder(convertView);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }

            final UserList list = getItem(position);
            if (list != null) {
                vh.checkBox.setText(list.getName());
                vh.checkBox.setChecked(membership.contains(list.getId()));

                if (updateTasks.containsValue(list)) {
                    vh.checkBox.setEnabled(false);
                    vh.progressBar.setVisibility(View.VISIBLE);
                } else {
                    vh.checkBox.setEnabled(true);
                    vh.progressBar.setVisibility(View.GONE);
                }
            }

            return convertView;
        }

        class ViewHolder {
            @InjectView(R.id.checkBox) CheckBox checkBox;
            @InjectView(R.id.progressBar) ProgressBar progressBar;

            public ViewHolder(View v) {
                ButterKnife.inject(this, v);
            }
        }
    }

    private class ListLoadTask extends ThrowableTwitterAsyncTask<AuthUserRecord, Pair<ResponseList<UserList>, List<Long>>> {
        private ListMembershipChecker checker;

        public ListLoadTask(ListMembershipChecker checker) {
            this.checker = checker;
        }

        @Override
        protected void showToast(String message) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }

        @Override
        protected ThrowableResult<Pair<ResponseList<UserList>, List<Long>>> doInBackground(AuthUserRecord... params) {
            try {
                Twitter twitter = delegate.getTwitterService().getTwitterOrThrow(params[0]);
                ResponseList<UserList> lists = twitter.getUserLists(params[0].NumericId);
                for (Iterator<UserList> iterator = lists.iterator(); iterator.hasNext(); ) {
                    UserList list = iterator.next();
                    if (list.getUser().getId() != params[0].NumericId) {
                        iterator.remove();
                    }
                }
                List<Long> membership = checker.findMembership(twitter, lists, targetUser.getId());
                return new ThrowableResult<>(new Pair<>(lists, membership));
            } catch (TwitterException e) {
                e.printStackTrace();
                return new ThrowableResult<>(e);
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            currentListLoadTask = null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(ThrowableResult<Pair<ResponseList<UserList>, List<Long>>> result) {
            super.onPostExecute(result);
            currentListLoadTask = null;
            if (!ListRegisterDialogFragment.this.isResumed()) {
                return;
            }
            progressBar.setVisibility(View.GONE);
            if (!result.isException()) {
                userLists = result.getResult().first;
                membership = result.getResult().second;
                adapter = new UserListAdapter(getActivity(), userLists);
                listView.setAdapter(adapter);
            }
        }
    }

    /**
     * リストにユーザが登録されているか取得する処理の定義
     */
    private enum ListMembershipChecker {
        /** ユーザ全探索 */
        MEMBERS_FULL_SEARCH {
            @Override
            public List<Long> findMembership(Twitter twitter, List<UserList> userLists, long targetUserId) {
                List<Long> membership = new ArrayList<>();
                for (UserList list : userLists) {
                    try {
                        for (long cursor = -1;;) {
                            PagableResponseList<User> userListMembers = twitter.getUserListMembers(list.getId(), cursor);
                            for (User userListMember : userListMembers) {
                                if (userListMember.getId() == targetUserId) {
                                    membership.add(list.getId());
                                    break;
                                }
                            }
                            if (userListMembers.hasNext()) {
                                cursor = userListMembers.getNextCursor();
                            } else break;
                        }
                    } catch (TwitterException ignored) {}
                }
                return membership;
            }
        },

        /** GET members/show を使って取得 */
        CHECK_MEMBERS_SHOW {
            @Override
            public List<Long> findMembership(Twitter twitter, List<UserList> userLists, long targetUserId) {
                List<Long> membership = new ArrayList<>();
                for (UserList list : userLists) {
                    try {
                        twitter.showUserListMembership(list.getId(), targetUserId);
                        membership.add(list.getId());
                    } catch (TwitterException ignored) {}
                }
                return membership;
            }
        },

        /** 探索しない */
        NO_SEARCH {
            @Override
            public List<Long> findMembership(Twitter twitter, List<UserList> userLists, long targetUserId) {
                return new ArrayList<>();
            }
        }
        ;

        public abstract List<Long> findMembership(Twitter twitter, List<UserList> userLists, long targetUserId);
    }

    private class ListUpdateTask extends ThrowableTwitterAsyncTask<ListUpdateTask.Params, ListUpdateTask.Params> {
        public static final int ADD = 0;
        public static final int REMOVE = 1;

        class Params {
            int mode;
            AuthUserRecord userRecord;
            UserList list;

            Params(int mode, AuthUserRecord userRecord, UserList list) {
                this.mode = mode;
                this.userRecord = userRecord;
                this.list = list;
            }
        }

        @Override
        protected ThrowableResult<Params> doInBackground(Params... params) {
            try {
                Twitter twitter = delegate.getTwitterService().getTwitterOrThrow(params[0].userRecord);
                switch (params[0].mode) {
                    case ADD:
                        twitter.createUserListMember(params[0].list.getId(), targetUser.getId());
                        break;
                    case REMOVE:
                        twitter.destroyUserListMember(params[0].list.getId(), targetUser.getId());
                        break;
                }
                return new ThrowableResult<>(params[0]);
            } catch (TwitterException e) {
                e.printStackTrace();
                return new ThrowableResult<>(e);
            }
        }

        @Override
        protected void showToast(String message) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(ThrowableResult<Params> result) {
            super.onPostExecute(result);
            if (!result.isException() && !isCancelled() && updateTasks.containsKey(this)) {
                Params params = result.getResult();
                switch (params.mode) {
                    case ADD:
                        membership.add(params.list.getId());
                        break;
                    case REMOVE:
                        membership.remove(params.list.getId());
                        break;
                }
                updateTasks.remove(this);
                adapter.notifyDataSetChanged();
            }
        }
    }
}
