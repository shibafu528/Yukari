package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.DialogFragment;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.Provider;
import shibafu.yukari.databinding.DialogListBinding;
import shibafu.yukari.databinding.RowCheckBinding;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by shibafu on 14/07/22.
 */
public class ListRegisterDialogFragment extends DialogFragment {
    DialogListBinding binding;

    private static final int REQUEST_CHOOSE = 1 << 8;
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
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getTargetFragment() != null && getTargetFragment() instanceof TwitterServiceDelegate) {
            delegate = (TwitterServiceDelegate) getTargetFragment();
        } else if (context instanceof TwitterServiceDelegate) {
            delegate = (TwitterServiceDelegate) context;
        } else {
            throw new RuntimeException("TwitterServiceDelegate cannot find.");
        }
        currentUser = App.getInstance(requireContext()).getAccountManager().findPreferredUser(Provider.API_TWITTER);

        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(ARG_TARGET_USER);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light").endsWith("dark")) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
        }

        binding = DialogListBinding.inflate(getLayoutInflater());
        binding.llMenuAccountParent.setOnClickListener(this::onClickTitle);
        binding.listView.setOnItemClickListener(this::onItemClick);

        binding.tvMenuTitle.setText(String.format("@%s のリスト", currentUser.ScreenName));
        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), binding.ivMenuAccountIcon, currentUser.ProfileImageUrl);

        dialog.setContentView(binding.getRoot());

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
        binding = null;
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
                        binding.tvMenuTitle.setText(String.format("@%s のリスト", currentUser.ScreenName));
                        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), binding.ivMenuAccountIcon, currentUser.ProfileImageUrl);
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

    void onClickTitle(View v) {
        Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
        intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_TWITTER);
        startActivityForResult(intent, REQUEST_CHOOSE);
    }

    void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
            RowCheckBinding vh;
            if (convertView == null) {
                vh = RowCheckBinding.inflate(inflater);
                convertView = vh.getRoot();
                convertView.setTag(vh);
            } else {
                vh = (RowCheckBinding) convertView.getTag();
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
                Twitter twitter = TwitterUtil.getTwitterOrThrow(requireContext(), params[0]);
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
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(ThrowableResult<Pair<ResponseList<UserList>, List<Long>>> result) {
            super.onPostExecute(result);
            currentListLoadTask = null;
            if (!ListRegisterDialogFragment.this.isResumed()) {
                return;
            }
            binding.progressBar.setVisibility(View.GONE);
            if (!result.isException()) {
                userLists = result.getResult().first;
                membership = result.getResult().second;
                adapter = new UserListAdapter(getActivity(), userLists);
                binding.listView.setAdapter(adapter);
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
                Twitter twitter = TwitterUtil.getTwitterOrThrow(requireContext(), params[0].userRecord);
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
