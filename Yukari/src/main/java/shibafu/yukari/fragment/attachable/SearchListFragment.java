package shibafu.yukari.fragment.attachable;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.util.Iterator;

import shibafu.yukari.common.TabType;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import twitter4j.DirectMessage;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterException;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarcompat.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchListFragment extends TweetListFragment implements OnRefreshListener, TwitterService.StatusListener {

    public static final String EXTRA_SEARCH_QUERY = "search_query";
    private String searchQuery;
    private Query nextQuery;
    private PullToRefreshLayout pullToRefreshLayout;
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

        ViewGroup viewGroup = (ViewGroup) view;

        Options.Builder options = new Options.Builder()
                .noMinimize();

        pullToRefreshLayout = new PullToRefreshLayout(viewGroup.getContext());
        ActionBarPullToRefresh.from(getActivity())
                .insertLayoutInto(viewGroup)
                .theseChildrenArePullable(getListView(), getListView().getEmptyView())
                .listener(this)
                .options(options.build())
                .setup(pullToRefreshLayout);

        if (getMode() == TabType.TABTYPE_TRACK) {
            streaming = true;
            pullToRefreshLayout.setEnabled(false);
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
    protected void onServiceConnected() {
        if (statuses.isEmpty()) {
            executeLoader(LOADER_LOAD_INIT, getCurrentUser());
        }
        getService().addStatusListener(this);
    }

    @Override
    protected void onServiceDisconnected() {
        getService().removeStatusListener(this);
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
            pullToRefreshLayout.setEnabled(false);
            getService().startFilterStream(searchQuery, getCurrentUser());
        }
        else {
            pullToRefreshLayout.setEnabled(true);
            getService().stopFilterStream(searchQuery);
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        executeLoader(LOADER_LOAD_UPDATE, getCurrentUser());
    }

    @Override
    public String getStreamFilter() {
        return searchQuery;
    }

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
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {}

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
            pullToRefreshLayout.setRefreshComplete();
        }
    }
}
