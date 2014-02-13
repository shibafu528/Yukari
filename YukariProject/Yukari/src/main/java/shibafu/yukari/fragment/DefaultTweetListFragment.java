package shibafu.yukari.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import shibafu.yukari.common.TabType;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/02/13.
 */
public class DefaultTweetListFragment extends TweetListFragment implements TwitterService.StatusListener {

    private Status traceStart = null;
    private User targetUser = null;

    private long lastStatusId = -1;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();

        int mode = getMode();
        if (mode == TabType.TABTYPE_TRACE) {
            traceStart = (Status) args.getSerializable(EXTRA_TRACE_START);
            if (statuses.isEmpty()) {
                statuses.add(new PreformedStatus(traceStart, getCurrentUser()));
            }
        }
        else if (mode == TabType.TABTYPE_USER || mode == TabType.TABTYPE_FAVORITE) {
            targetUser = (User) args.getSerializable(EXTRA_SHOW_USER);
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
    public void onDestroy() {
        if (isServiceBound()) {
            getService().removeStatusListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        DefaultRESTLoader loader = new DefaultRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
                loader.execute(loader.new Params(userRecord));
                break;
            case LOADER_LOAD_MORE:
                loader.execute(loader.new Params(lastStatusId, userRecord));
                break;
        }
    }

    @Override
    protected void onServiceConnected() {
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
                                        statuses.add(location, ps);
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
        else if (statuses.isEmpty()) {
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_INIT, user);
            }
        }

        if (getMode() == TabType.TABTYPE_HOME || getMode() == TabType.TABTYPE_MENTION) {
            getService().addStatusListener(this);
        }
    }

    @Override
    protected void onServiceDisconnected() {

    }

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
    public void onStatus(AuthUserRecord from, final PreformedStatus status) {
        if (users.contains(from) && !statuses.contains(status)) {
            if (getMode() == TabType.TABTYPE_MENTION &&
                    ( !status.isMentionedToMe() || status.isRetweet() )) return;

            final int position = prepareInsertStatus(status);
            if (position > -1) {
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!statuses.contains(status)) {
                            if (position < statuses.size())  {
                                if (statuses.get(position).getId() == status.getId()) return;
                            }
                            statuses.add(position, status);
                            adapterWrap.notifyDataSetChanged();
                            if (statuses.size() == 1 || listView.getFirstVisiblePosition() < 2) {
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
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {
        //TODO: DM受信時の処理
    }

    @Override
    public void onDelete(AuthUserRecord from, final StatusDeletionNotice statusDeletionNotice) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                Iterator<PreformedStatus> iterator = statuses.iterator();
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

    private class DefaultRESTLoader
            extends RESTLoader<DefaultRESTLoader.Params, PreformedResponseList<PreformedStatus>> {
        class Params {
            private Paging paging;
            private AuthUserRecord userRecord;

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

            public Paging getPaging() {
                return paging;
            }

            public AuthUserRecord getUserRecord() {
                return userRecord;
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
                }
                if (responseList == null) {
                    lastStatusId = -1;
                }
                else if (responseList.size() > 0) {
                    lastStatusId = responseList.get(responseList.size() - 1).getId();
                }
                return PRListFactory.create(responseList, params[0].getUserRecord());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
