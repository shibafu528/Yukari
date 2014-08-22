package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.LongSparseArray;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import shibafu.yukari.R;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/03/25.
 */
public class MessageListFragment extends TwitterListFragment<DirectMessage>
        implements StatusManager.StatusListener, DialogInterface.OnClickListener {

    //ListView Adapter Wrapper
    private TweetAdapterWrap adapterWrap;

    //SwipeRefreshLayout
    private SwipeRefreshLayout swipeRefreshLayout;
    private int refreshCounter;

    private LongSparseArray<Long> lastStatusIds = new LongSparseArray<>();
    private DirectMessage lastClicked;

    private long lastShowedFirstItemId = -1;
    private int lastShowedFirstItemY = 0;
    private View unreadNotifierView;
    private Set<Long> unreadSet = new HashSet<>();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(getActivity(), users, elements, DirectMessage.class);
        setListAdapter(adapterWrap.getAdapter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_swipelist, container, false);

        swipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorScheme(
                R.color.key_color,
                R.color.key_color_2,
                R.color.key_color,
                R.color.key_color_2
        );
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                for (AuthUserRecord user : users) {
                    executeLoader(LOADER_LOAD_UPDATE, user);
                    ++refreshCounter;
                }
            }
        });

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        unreadNotifierView = view.findViewById(R.id.unreadNotifier);
        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_light);
                break;
            case "dark":
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_dark);
                break;
        }

        getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                for (; firstVisibleItem < firstVisibleItem + visibleItemCount && firstVisibleItem < elements.size(); ++firstVisibleItem) {
                    DirectMessage message = elements.get(firstVisibleItem);
                    if (message != null && unreadSet.contains(message.getId())) {
                        unreadSet.remove(message.getId());
                    }
                }
                updateUnreadNotifier();
            }
        });
    }

    @Override
    public void onDetach() {
        if (isServiceBound()) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDetach();
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
    public boolean isCloseable() {
        return false;
    }

    @Override
    public void onListItemClick(DirectMessage clickedElement) {
        lastClicked = clickedElement;
        MessageMenuDialogFragment dialogFragment = MessageMenuDialogFragment.newInstance(clickedElement);
        dialogFragment.setTargetFragment(this, 1);
        dialogFragment.show(getFragmentManager(), "menu");
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        MessageRESTLoader loader = new MessageRESTLoader();
        switch (requestMode) {
            case LOADER_LOAD_INIT:
                loader.execute(loader.new Params(userRecord));
                break;
            case LOADER_LOAD_MORE:
                loader.execute(loader.new Params(lastStatusIds.get(userRecord.NumericId, -1L), userRecord));
                break;
            case LOADER_LOAD_UPDATE:
                unreadSet.clear();
                unreadSet.clear();
                loader.execute(loader.new Params(userRecord, true));
                break;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (lastShowedFirstItemId > -1) {
            int position;
            int length = elements.size();
            for (position = 0; position < length; ++position) {
                if (elements.get(position).getId() == lastShowedFirstItemId) break;
            }
            if (position < length) {
                listView.setSelectionFromTop(position, lastShowedFirstItemY);
            }
        }
        updateUnreadNotifier();
    }

    @Override
    public void onStop() {
        super.onStop();
        lastShowedFirstItemId = listView.getItemIdAtPosition(listView.getFirstVisiblePosition());
        lastShowedFirstItemY = listView.getChildAt(0).getTop();
    }

    private void updateUnreadNotifier() {
        if (unreadSet.size() < 1) {
            unreadNotifierView.setVisibility(View.INVISIBLE);
            return;
        }
        TextView tv = (TextView) unreadNotifierView.findViewById(R.id.textView);
        tv.setText(String.format("未読 %d件", unreadSet.size()));

        unreadNotifierView.setVisibility(View.VISIBLE);
    }

    protected void setRefreshComplete() {
        if (--refreshCounter < 1 && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        for (AuthUserRecord userRecord : users) {
            executeLoader(LOADER_LOAD_INIT, userRecord);
        }

        getStatusManager().addStatusListener(this);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public String getStreamFilter() {
        return null;
    }

    @Override
    public void onStatus(AuthUserRecord from, PreformedStatus status, boolean muted) {}

    @Override
    public void onDirectMessage(AuthUserRecord from, final DirectMessage directMessage) {
        if (users.contains(from) && !elements.contains(directMessage)) {
            final int position = prepareInsertStatus(directMessage);
            if (position > -1) {
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!elements.contains(directMessage)) {
                            if (position < elements.size())  {
                                if (elements.get(position).getId() == directMessage.getId()) return;
                            }
                            elements.add(position, directMessage);
                            int firstPos = listView.getFirstVisiblePosition();
                            int y = listView.getChildAt(0).getTop();
                            adapterWrap.notifyDataSetChanged();
                            if (elements.size() == 1 || firstPos == 0 && y > -1) {
                                listView.setSelection(0);
                            } else {
                                unreadSet.add(directMessage.getId());
                                listView.setSelectionFromTop(firstPos + 1, y);
                            }
                            updateUnreadNotifier();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onUpdatedStatus(AuthUserRecord from, int kind, final Status status) {
        if (kind == StatusManager.UPDATE_DELETED_DM) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    Iterator<DirectMessage> iterator = elements.iterator();
                    while (iterator.hasNext()) {
                        if (iterator.next().getId() == status.getId()) {
                            int firstPos = listView.getFirstVisiblePosition();
                            long firstId = listView.getItemIdAtPosition(firstPos);
                            int y = listView.getChildAt(0).getTop();
                            iterator.remove();
                            adapterWrap.notifyDataSetChanged();
                            if (elements.size() == 1 || firstPos == 0) {
                                listView.setSelection(0);
                            } else {
                                listView.setSelectionFromTop(firstPos - (firstId < status.getId()? 1 : 0), y);
                            }
                            if (unreadSet.contains(status.getId())) {
                                unreadSet.remove(status.getId());
                                updateUnreadNotifier();
                            }
                            break;
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (lastClicked != null) {
            switch (which) {
                case 0:
                {
                    Intent intent = new Intent(getActivity(), TweetActivity.class);
                    intent.putExtra(TweetActivity.EXTRA_USER, findUserRecord(lastClicked.getRecipient()));
                    intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                    intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, lastClicked.getSenderId());
                    intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, lastClicked.getSenderScreenName());
                    startActivity(intent);
                    break;
                }
                case 1:
                {
                    MessageDeleteFragment fragment = new MessageDeleteFragment();
                    Bundle args = new Bundle();
                    args.putSerializable("message", lastClicked);
                    fragment.setArguments(args);
                    fragment.setTargetFragment(this, 0);
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.add(fragment, "deletemsg").commit();
                    break;
                }
                case 2:
                {
                    Intent intent = new Intent(getActivity(), ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER, findUserRecord(lastClicked.getRecipient()));
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, lastClicked.getSenderId());
                    startActivity(intent);
                    break;
                }
            }
        }
    }

    public void requestDeleteMessage(final DirectMessage message) {
        cancelledDeleteMessage();
        new ThrowableTwitterAsyncTask<DirectMessage, Boolean>() {

            @Override
            protected ThrowableResult<Boolean> doInBackground(DirectMessage... params) {
                AuthUserRecord user = null;
                for (AuthUserRecord userRecord : getService().getUsers()) {
                    if (params[0].getRecipientId() == userRecord.NumericId
                        || params[0].getSenderId() == userRecord.NumericId) {
                        user = userRecord;
                    }
                }
                if (user == null) {
                    return new ThrowableResult<>(new IllegalArgumentException("操作対象のユーザが見つかりません."));
                }
                Twitter t = getService().getTwitter();
                t.setOAuthAccessToken(user.getAccessToken());
                try {
                    t.destroyDirectMessage(message.getId());
                } catch (TwitterException e) {
                    e.printStackTrace();
                    return new ThrowableResult<>(e);
                }
                return new ThrowableResult<>(true);
            }

            @Override
            protected void onPostExecute(ThrowableResult<Boolean> result) {
                super.onPostExecute(result);
                if (!isErrored() && result.getResult()) {
                    showToast("削除しました");
                }
            }

            @Override
            protected void showToast(String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            }
        }.execute(message);
    }

    public void cancelledDeleteMessage() {
        FragmentManager manager = getFragmentManager();
        Fragment fragment = manager.findFragmentByTag("deletemsg");
        manager.beginTransaction().remove(fragment).commit();
    }

    private AuthUserRecord findUserRecord(User user) {
        for (AuthUserRecord userRecord : users) {
            if (userRecord.NumericId == user.getId()) {
                return userRecord;
            }
        }
        return null;
    }

    private class MessageRESTLoader extends AsyncTask<MessageRESTLoader.Params, Void, List<DirectMessage>> {
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

        @Override
        protected List<DirectMessage> doInBackground(Params... params) {
            twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
            try {
                Paging paging = params[0].getPaging();
                paging.setCount(60);
                ResponseList<DirectMessage> inBoxResponse = twitter.getDirectMessages(paging);
                ResponseList<DirectMessage> sentBoxResponse = twitter.getSentDirectMessages(paging);
                if (!params[0].isSaveLastPaging()) {
                    long lastStatusId;
                    if (inBoxResponse != null && !inBoxResponse.isEmpty() &&
                            sentBoxResponse != null && !sentBoxResponse.isEmpty()) {
                        lastStatusId = Math.min(inBoxResponse.get(inBoxResponse.size() - 1).getId(),
                                sentBoxResponse.get(sentBoxResponse.size() - 1).getId());
                    }
                    else if (inBoxResponse != null && !inBoxResponse.isEmpty()) {
                        lastStatusId = inBoxResponse.get(inBoxResponse.size() - 1).getId();
                    }
                    else if (sentBoxResponse != null && !sentBoxResponse.isEmpty()) {
                        lastStatusId = sentBoxResponse.get(sentBoxResponse.size() - 1).getId();
                    }
                    else {
                        lastStatusId = -1;
                    }
                    lastStatusIds.put(params[0].getUserRecord().NumericId, lastStatusId);
                }
                List<DirectMessage> response = new ArrayList<>();
                if (inBoxResponse != null) {
                    response.addAll(inBoxResponse);
                }
                if (sentBoxResponse != null) {
                    response.addAll(sentBoxResponse);
                }
                return response;
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
        protected void onPostExecute(List<DirectMessage> directMessages) {
            if (directMessages != null) {
                int position;
                for (DirectMessage message : directMessages) {
                    position = prepareInsertStatus(message);
                    if (position > -1) {
                        elements.add(position, message);
                    }
                }
                adapterWrap.notifyDataSetChanged();
            }
            changeFooterProgress(false);
            setRefreshComplete();
        }
    }

    public static class MessageMenuDialogFragment extends DialogFragment implements DialogInterface.OnClickListener{

        public static MessageMenuDialogFragment newInstance(DirectMessage message) {
            MessageMenuDialogFragment fragment = new MessageMenuDialogFragment();
            Bundle args = new Bundle();
            String screenName = message.getSenderScreenName();
            args.putString("text", "@" + screenName + ":" + message.getText());
            args.putString("sender", screenName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            List<String> items = new ArrayList<>(Arrays.asList(new String[]{
                    "返信する",
                    "削除",
                    "@" + args.getString("sender")
            }));
            return new AlertDialog.Builder(getActivity())
                    .setTitle(args.getString("text"))
                    .setItems(items.toArray(new String[items.size()]), this)
                    .setCancelable(true)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            if (getParentFragment() != null && getParentFragment() instanceof DialogInterface.OnClickListener) {
                ((DialogInterface.OnClickListener) getParentFragment()).onClick(dialog, which);
            }
            else if (getTargetFragment() != null && getTargetFragment() instanceof DialogInterface.OnClickListener) {
                ((DialogInterface.OnClickListener) getTargetFragment()).onClick(dialog, which);
            }
        }
    }

    public static class MessageDeleteFragment extends Fragment implements DialogInterface.OnClickListener{
        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    "確認",
                    "メッセージを削除してもよろしいですか？",
                    "OK",
                    "キャンセル");
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getFragmentManager(), "msdeld");
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                Bundle args = getArguments();
                DirectMessage message = (DirectMessage) args.getSerializable("message");
                ((MessageListFragment) getTargetFragment()).requestDeleteMessage(message);
            }
            else {
                ((MessageListFragment) getTargetFragment()).cancelledDeleteMessage();
            }
        }
    }
}
