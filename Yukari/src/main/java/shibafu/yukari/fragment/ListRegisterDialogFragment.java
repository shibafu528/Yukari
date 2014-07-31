package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnItemClick;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;

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
    private ArrayList<Long> membership;

    private ThrowableTwitterAsyncTask<AuthUserRecord, Pair<ResponseList<UserList>, ArrayList<Long>>> currentTask;
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
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);

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

        loadList();

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        ButterKnife.reset(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
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
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        currentTask = new ThrowableTwitterAsyncTask<AuthUserRecord, Pair<ResponseList<UserList>, ArrayList<Long>>>() {
            @Override
            protected void showToast(String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            protected ThrowableResult<Pair<ResponseList<UserList>, ArrayList<Long>>> doInBackground(AuthUserRecord... params) {
                Twitter twitter = delegate.getTwitterService().getTwitter();
                twitter.setOAuthAccessToken(params[0].getAccessToken());
                try {
                    ResponseList<UserList> lists = twitter.getUserLists(params[0].NumericId);
                    ArrayList<Long> membership = new ArrayList<>();
                    for (Iterator<UserList> iterator = lists.iterator(); iterator.hasNext(); ) {
                        UserList list = iterator.next();
                        if (list.getUser().getId() != params[0].NumericId) {
                            iterator.remove();
                        } else {
                            try {
                                for (long cursor = -1;;) {
                                    PagableResponseList<User> userListMembers = twitter.getUserListMembers(list.getId(), cursor);
                                    for (User userListMember : userListMembers) {
                                        if (userListMember.getId() == targetUser.getId()) {
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
                    }
                    return new ThrowableResult<>(new Pair<>(lists, membership));
                } catch (TwitterException e) {
                    e.printStackTrace();
                    return new ThrowableResult<>(e);
                }
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                currentTask = null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onPostExecute(ThrowableResult<Pair<ResponseList<UserList>, ArrayList<Long>>> result) {
                super.onPostExecute(result);
                currentTask = null;
                progressBar.setVisibility(View.GONE);
                if (!result.isException()) {
                    userLists = result.getResult().first;
                    membership = result.getResult().second;
                    adapter = new UserListAdapter(getActivity(), userLists);
                    listView.setAdapter(adapter);
                }
            }
        };
        currentTask.executeParallel(currentUser);
    }

    @OnClick(R.id.llMenuAccountParent)
    void onClickTitle() {
        startActivityForResult(new Intent(getActivity(), AccountChooserActivity.class), REQUEST_CHOOSE);
    }

    @OnItemClick(R.id.listView)
    void onItemClick(int position) {
        long listId = userLists.get(position).getId();
        if (!membership.contains(listId)) {
            membership.add(listId);
        } else {
            membership.remove(listId);
        }
        adapter.notifyDataSetChanged();
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
            }

            return convertView;
        }

        class ViewHolder {
            @InjectView(R.id.checkBox)
            CheckBox checkBox;

            public ViewHolder(View v) {
                ButterKnife.inject(this, v);
            }
        }
    }
}
