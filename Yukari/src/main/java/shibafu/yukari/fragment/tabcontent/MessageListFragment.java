package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.LongSparseArray;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
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

    private LongSparseArray<Long> lastStatusIds = new LongSparseArray<>();
    private DirectMessage lastClicked;

    public MessageListFragment() {
        super(DirectMessage.class);
    }

    @Override
    public void onDetach() {
        if (isServiceBound()) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDetach();
    }


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
                clearUnreadNotifier();
                loader.execute(loader.new Params(userRecord, true));
                break;
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
                        insertElement(directMessage, position);
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
                    deleteElement(status);
                }
            });
        } else if (kind == StatusManager.UPDATE_WIPE_TWEETS) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    elements.clear();
                    adapterWrap.notifyDataSetChanged();
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
