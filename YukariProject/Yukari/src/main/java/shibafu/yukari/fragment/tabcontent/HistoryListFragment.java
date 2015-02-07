package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import android.view.View;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.statusimpl.HistoryStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * Created by shibafu on 15/02/07.
 */
public class HistoryListFragment extends TwitterListFragment<HistoryStatus> implements StatusManager.StatusListener {

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
    public void onListItemClick(HistoryStatus clickedElement) {

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
            final HistoryStatus historyStatus = (HistoryStatus) status;
            final int position = prepareInsertStatus(historyStatus);
            if (position > -1) {
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        insertElement(historyStatus, position);
                    }
                });
            }
        }
    }
}
