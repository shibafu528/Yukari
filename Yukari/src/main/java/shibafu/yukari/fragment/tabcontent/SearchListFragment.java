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
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.util.ReferenceHolder;
import twitter4j.*;

import java.util.Iterator;

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
            getStatusManager().stopFilterStream(parsedQuery.getValidQuery());
        }
    }

    @Override
    public String getStreamFilter() {
        return parsedQuery.getValidQuery();
    }

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status, boolean muted) {
        if (users.contains(from) && !elements.contains(status)) {
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

    @Override
    public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {}

    @Override
    public void onUpdatedStatus(final AuthUserRecord from, int kind, final Status status) {
        switch (kind) {
            case StatusManager.UPDATE_WIPE_TWEETS:
                getHandler().post(() -> {
                    elements.clear();
                    notifyDataSetChanged();
                });
                stash.clear();
                break;
            case StatusManager.UPDATE_FORCE_UPDATE_UI:
                getHandler().post(this::notifyDataSetChanged);
                break;
            case StatusManager.UPDATE_DELETED:
                getHandler().post(() -> deleteElement(status));
                for (Iterator<PreformedStatus> iterator = stash.iterator(); iterator.hasNext(); ) {
                    if (iterator.next().getId() == status.getId()) {
                        iterator.remove();
                    }
                }
                break;
            case StatusManager.UPDATE_FAVED:
            case StatusManager.UPDATE_UNFAVED:
                int position = 0;
                for (; position < elements.size(); ++position) {
                    if (elements.get(position).getId() == status.getId()) break;
                }
                if (position < elements.size()) {
                    final int p = position;
                    getHandler().post(() -> {
                        elements.get(p).merge(status, from);
                        notifyDataSetChanged();
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

        private boolean isNarrowMode;

        protected SearchRESTLoader(RESTLoaderInterface loaderInterface) {
            super(loaderInterface);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isNarrowMode = sp.getBoolean("pref_narrow", false);
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
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
