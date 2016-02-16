package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusmanager.StatusListener;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.util.ReferenceHolder;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchListFragment extends TweetListFragment implements StatusListener {

    public static final String EXTRA_SEARCH_QUERY = "search_query";
    private String searchQuery;
    private FilterStream.ParsedQuery parsedQuery;
    private ReferenceHolder<Query> queryHolder = new ReferenceHolder<>();
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
        parsedQuery = new FilterStream.ParsedQuery(searchQuery);

        if (getMode() == TabType.TABTYPE_TRACK) {
            streaming = true;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof MainActivity) {
            Bundle args = getArguments();
            long id = args.getLong(EXTRA_ID);
            queryHolder = ((MainActivity) activity).getSearchQuery(id);
        }
    }

    @Override
    public void onDetach() {
        if (isServiceBound() && getStatusManager() != null) {
            getStatusManager().removeStatusListener(this);
        }
        super.onDetach();
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        SearchRESTLoader restLoader = new SearchRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
            case LOADER_LOAD_UPDATE:
                clearUnreadNotifier();
                restLoader.execute(restLoader.new Params(userRecord, searchQuery));
                break;
            case LOADER_LOAD_MORE:
                if (queryHolder.getReference() != null) {
                    addLimitCount(100);
                    restLoader.execute(restLoader.new Params(userRecord, queryHolder.getReference()));
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
        if (getStatusManager() != null) {
            getStatusManager().removeStatusListener(this);
        }
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
            getStatusManager().startFilterStream(parsedQuery.getValidQuery(), getCurrentUser());
        }
        else {
            getStatusManager().stopFilterStream(parsedQuery.getValidQuery(), getCurrentUser());
        }
    }

    @Override
    public String getStreamFilter() {
        return parsedQuery.getValidQuery();
    }

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status, boolean muted) {
        if (users.contains(from) && !elements.contains(status) && status.getText().contains(parsedQuery.getValidQuery())) {
            if (getMode() == TabType.TABTYPE_MENTION &&
                    ( !status.isMentionedToMe() || status.isRetweet() )) return;

            if (muted) {
                stash.add(status);
            }
            else if (!PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_search_minus_rt", false) || !status.isRetweet()) {
                final int position = prepareInsertStatus(status);
                if (position > -1) {
                    getHandler().post(() -> insertElement(status, position));
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

        private boolean isNarrowMode;

        protected SearchRESTLoader(RESTLoaderInterface loaderInterface) {
            super(loaderInterface);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isNarrowMode = sp.getBoolean("pref_narrow", false);
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            Twitter twitter = getTwitterService().getTwitter(params[0].getUserRecord());
            try {
                Query query = params[0].getQuery();
                query.setCount(isNarrowMode ? 20 : 100);
                QueryResult result = twitter.search(query);
                queryHolder.setReference(result.nextQuery());
                return PRListFactory.create(result, params[0].getUserRecord());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(PreformedResponseList<PreformedStatus> result) {
            super.onPostExecute(result);
            if (queryHolder.getReference() == null) {
                removeFooter();
            }
            setRefreshComplete();
        }
    }
}
