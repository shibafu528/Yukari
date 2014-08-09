package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import android.view.View;

import java.util.Iterator;

import shibafu.yukari.common.TabType;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.StatusManager;
import twitter4j.DirectMessage;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchListFragment extends TweetListFragment implements StatusManager.StatusListener {

    public static final String EXTRA_SEARCH_QUERY = "search_query";
    private String searchQuery;
    private Query nextQuery;
    private boolean streaming;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        searchQuery = args.getString(EXTRA_SEARCH_QUERY);
        if (searchQuery == null) {
            //本来は検索ボックスとかで空白やnullに対して適切な処理をするべき
            //どうしてもnullが渡ってきてしまった場合はこうしてこうじゃ
            searchQuery = "ﾕｯｶﾘｰﾝ";
        }

        if (getMode() == TabType.TABTYPE_TRACK) {
            streaming = true;
        }
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        SearchRESTLoader restLoader = new SearchRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
            case LOADER_LOAD_UPDATE:
                restLoader.execute(restLoader.new Params(userRecord, searchQuery));
                break;
            case LOADER_LOAD_MORE:
                if (nextQuery != null) {
                    restLoader.execute(restLoader.new Params(userRecord, nextQuery));
                }
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        if (elements.isEmpty()) {
            executeLoader(LOADER_LOAD_INIT, getCurrentUser());
        }
        getStatusManager().addStatusListener(this);
    }

    @Override
    public void onServiceDisconnected() {
        getStatusManager().removeStatusListener(this);
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
        if (streaming) {
            getStatusManager().startFilterStream(searchQuery, getCurrentUser());
        }
        else {
            getStatusManager().stopFilterStream(searchQuery);
        }
    }

    @Override
    public String getStreamFilter() {
        return searchQuery;
    }

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status, boolean muted) {
        if (users.contains(from) && !elements.contains(status)) {
            if (getMode() == TabType.TABTYPE_MENTION &&
                    ( !status.isMentionedToMe() || status.isRetweet() )) return;

            if (muted) {
                stash.add(status);
            }
            else {
                final int position = prepareInsertStatus(status);
                if (position > -1) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (!elements.contains(status)) {
                                if (position < elements.size()) {
                                    if (elements.get(position).getId() == status.getId()) return;
                                }
                                elements.add(position, status);
                                int firstPos = listView.getFirstVisiblePosition();
                                int y = listView.getChildAt(0).getTop();
                                adapterWrap.notifyDataSetChanged();
                                if (elements.size() == 1 || firstPos == 0 && y > -1) {
                                    listView.setSelection(0);
                                } else {
                                    listView.setSelectionFromTop(firstPos + 1, y);
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

    private class SearchRESTLoader
            extends RESTLoader<SearchRESTLoader.Params, PreformedResponseList<PreformedStatus>> {
        class Params {
            private AuthUserRecord userRecord;
            private Query query;

            public Params(AuthUserRecord userRecord, String query) {
                this.query = new Query(query);
                this.query.setCount(100);
                this.query.setResultType(Query.RECENT);
                this.userRecord = userRecord;
            }

            public Params(AuthUserRecord userRecord, Query query) {
                this.query = query;
                this.userRecord = userRecord;
            }

            public Query getQuery() {
                return query;
            }

            public AuthUserRecord getUserRecord() {
                return userRecord;
            }
        }

        protected SearchRESTLoader(RESTLoaderInterface loaderInterface) {
            super(loaderInterface);
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
            try {
                QueryResult result = twitter.search(params[0].getQuery());
                nextQuery = result.nextQuery();
                return PRListFactory.create(result, params[0].getUserRecord());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(PreformedResponseList<PreformedStatus> result) {
            super.onPostExecute(result);
            if (nextQuery == null) {
                removeFooter();
            }
            setRefreshComplete();
        }
    }
}
