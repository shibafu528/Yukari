package shibafu.yukari.fragment.tabcontent;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.util.LongSparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.StatusManager;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/03/25.
 */
public class MessageListFragment extends TwitterListFragment<DirectMessage>
        implements StatusManager.StatusListener, DialogInterface.OnClickListener {

    //ListView Adapter Wrapper
    private TweetAdapterWrap adapterWrap;

    private LongSparseArray<Long> lastStatusIds = new LongSparseArray<>();
    private DirectMessage lastClicked;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(
                getActivity().getApplicationContext(), users, elements, DirectMessage.class);
        setListAdapter(adapterWrap.getAdapter());
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
    public void onDestroy() {
        if (isServiceBound()) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDestroy();
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
                loader.execute(loader.new Params(userRecord, true));
                break;
        }
    }

    @Override
    protected void onServiceConnected() {
        for (AuthUserRecord userRecord : users) {
            executeLoader(LOADER_LOAD_INIT, userRecord);
        }

        getStatusManager().addStatusListener(this);
    }

    @Override
    protected void onServiceDisconnected() {}

    @Override
    public String getStreamFilter() {
        return null;
    }

    @Override
    public void onStatus(AuthUserRecord from, PreformedStatus status) {}

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

    @Override
    public void onDelete(AuthUserRecord from, final StatusDeletionNotice statusDeletionNotice) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                Iterator<DirectMessage> iterator = elements.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getId() == statusDeletionNotice.getStatusId()) {
                        iterator.remove();
                        adapterWrap.notifyDataSetChanged();
                        break;
                    }
                }
            }
        });
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
                    Intent intent = new Intent(getActivity(), ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER, findUserRecord(lastClicked.getRecipient()));
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, lastClicked.getSenderId());
                    startActivity(intent);
                    break;
                }
            }
        }
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
                    //"削除",
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
}
