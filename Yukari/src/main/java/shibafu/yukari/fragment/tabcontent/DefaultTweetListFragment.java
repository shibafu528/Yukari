package shibafu.yukari.fragment.tabcontent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.util.LongSparseArray;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;

import shibafu.yukari.common.TabType;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.StatusManager;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/02/13.
 */
public class DefaultTweetListFragment extends TweetListFragment implements StatusManager.StatusListener {

    public static final String EXTRA_LIST_ID = "listid";
    public static final String EXTRA_TRACE_START = "trace_start";

    private Status traceStart = null;
    private User targetUser = null;
    private long listId = -1;

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
        }
        else if (elements.isEmpty()) {
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_INIT, user);
            }
        }

        getStatusManager().addStatusListener(this);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public boolean isCloseable() {
        return false;
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
                        responseList = twitter.getUserListStatuses((int)listId, paging);
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
