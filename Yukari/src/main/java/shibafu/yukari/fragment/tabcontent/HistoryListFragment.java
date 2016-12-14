package shibafu.yukari.fragment.tabcontent;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.fragment.SimpleListDialogFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusmanager.StatusListener;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.twitter.statusimpl.HistoryStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * Created by shibafu on 15/02/07.
 */
public class HistoryListFragment extends TwitterListFragment<HistoryStatus> implements StatusListener, SimpleListDialogFragment.OnDialogChoseListener {

    private HistoryStatus lastClicked;

    public HistoryListFragment() {
        super(HistoryStatus.class);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        disableReload();
    }

    @Override
    public void onDetach() {
        if (isServiceBound() && getStatusManager() != null) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDetach();
    }


    @Override
    public boolean isCloseable() {
        return false;
    }

    @Override
    public boolean onListItemClick(int position, HistoryStatus clickedElement) {
        lastClicked = clickedElement;
        SimpleListDialogFragment dialogFragment = SimpleListDialogFragment.newInstance(
                0, "メニュー", null, null, null,
                "@" + clickedElement.getUser().getScreenName(),
                "ツイートを開く");
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(getFragmentManager(), "menu");
        return true;
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {}

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
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
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {}

    @Override
    public void onUpdatedStatus(AuthUserRecord from, int kind, Status status) {
        if (kind == StatusManager.UPDATE_NOTIFY && status instanceof HistoryStatus) {
            getHandler().post(() -> insertElement2((HistoryStatus) status));
        }
    }

    @Override
    public void onDialogChose(int requestCode, int which, String value) {
        if (requestCode == 0) {
            Intent intent = new Intent();
            switch (which) {
                case 0:
                    intent.setClass(getActivity(), ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER, getTwitterService().isMyTweet(lastClicked.getStatus()));
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, lastClicked.getUser().getId());
                    startActivity(intent);
                    break;
                case 1:
                    intent.setClass(getActivity(), StatusActivity.class);
                    intent.putExtra(StatusActivity.EXTRA_STATUS, lastClicked.getStatus());
                    intent.putExtra(StatusActivity.EXTRA_USER, getTwitterService().isMyTweet(lastClicked.getStatus()));
                    startActivity(intent);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    setBlockingDoubleClick(false);
                    break;
            }
        }
    }
}
