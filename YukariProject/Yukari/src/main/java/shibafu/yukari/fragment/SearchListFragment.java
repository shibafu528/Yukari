package shibafu.yukari.fragment;

import android.os.Bundle;
import android.view.View;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchListFragment extends TweetListFragment {

    public static final String EXTRA_SEARCH_QUERY = "search_query";
    private String searchQuery;
    private Query nextQuery;

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
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        SearchRESTLoader restLoader = new SearchRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
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
        executeLoader(LOADER_LOAD_INIT, getCurrentUser());
    }

    @Override
    protected void onServiceDisconnected() {

    }

    @Override
    public boolean isCloseable() {
        return true;
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
        }
    }
}
