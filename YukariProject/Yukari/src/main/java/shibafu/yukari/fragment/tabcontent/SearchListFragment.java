package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AbsListView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import shibafu.yukari.R;
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

    private long lastShowedFirstItemId = -1;
    private int lastShowedFirstItemY = 0;
    private View unreadNotifierView;
    private Set<Long> unreadSet = new HashSet<>();

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

        unreadNotifierView = view.findViewById(R.id.unreadNotifier);
        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_light);
                break;
            case "dark":
                unreadNotifierView.setBackgroundResource(R.drawable.dialog_full_holo_dark);
                break;
        }

        getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                for (; firstVisibleItem < firstVisibleItem + visibleItemCount && firstVisibleItem < elements.size(); ++firstVisibleItem) {
                    PreformedStatus status = elements.get(firstVisibleItem);
                    if (status != null && unreadSet.contains(status.getId())) {
                        unreadSet.remove(status.getId());
                    }
                }
                updateUnreadNotifier();
            }
        });
    }

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        SearchRESTLoader restLoader = new SearchRESTLoader(getDefaultRESTInterface());
        switch (requestMode) {
            case LOADER_LOAD_INIT:
            case LOADER_LOAD_UPDATE:
                unreadSet.clear();
                restLoader.execute(restLoader.new Params(userRecord, searchQuery));
                break;
            case LOADER_LOAD_MORE:
                if (nextQuery != null) {
                    restLoader.execute(restLoader.new Params(userRecord, nextQuery));
                }
                break;
        }
    }

    private void updateUnreadNotifier() {
        if (unreadSet.size() < 1) {
            unreadNotifierView.setVisibility(View.INVISIBLE);
            return;
        }
        TextView tv = (TextView) unreadNotifierView.findViewById(R.id.textView);
        tv.setText(String.format("新着 %d件", unreadSet.size()));

        unreadNotifierView.setVisibility(View.VISIBLE);
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
    public void onStart() {
        super.onStart();
        if (lastShowedFirstItemId > -1) {
            int position;
            int length = elements.size();
            for (position = 0; position < length; ++position) {
                if (elements.get(position).getId() == lastShowedFirstItemId) break;
            }
            if (position < length) {
                listView.setSelectionFromTop(position, lastShowedFirstItemY);
            }
        }
        updateUnreadNotifier();
    }

    @Override
    public void onStop() {
        super.onStop();
        lastShowedFirstItemId = listView.getItemIdAtPosition(listView.getFirstVisiblePosition());
        lastShowedFirstItemY = listView.getChildAt(0).getTop();
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
                                    unreadSet.add(status.getId());
                                    listView.setSelectionFromTop(firstPos + 1, y);
                                }
                                updateUnreadNotifier();
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
                                if (unreadSet.contains(status.getId())) {
                                    unreadSet.remove(status.getId());
                                    updateUnreadNotifier();
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
