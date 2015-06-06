package shibafu.yukari.fragment.tabcontent;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.util.Iterator;

import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * Created by shibafu on 14/02/13.
 */
public class FilterListFragment extends TweetListFragment implements StatusManager.StatusListener {

    public static final String EXTRA_FILTER_QUERY = "filterQuery";

    private String filterRawQuery;
    private FilterQuery filterQuery;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Bundle args = getArguments();
        filterRawQuery = args.getString(EXTRA_FILTER_QUERY);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().registerReceiver(onReloadReceiver, new IntentFilter(TwitterService.RELOADED_USERS));
        getActivity().registerReceiver(onActiveChangedReceiver, new IntentFilter(TwitterService.CHANGED_ACTIVE_STATE));
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(onReloadReceiver);
        getActivity().unregisterReceiver(onActiveChangedReceiver);
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
        getFilterQuery();
        switch (requestMode) {
            case LOADER_LOAD_UPDATE:
                //今はまだ無視する
                setRefreshComplete();
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        //ユーザ情報を取得
        users = getTwitterService().getUsers();

        //クエリのコンパイルを開始
        filterQuery = QueryCompiler.compile(users, filterRawQuery);

        //ストリーミングのリスナ登録
        getStatusManager().addStatusListener(this);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public boolean isCloseable() {
        return false;
    }

    public FilterQuery getFilterQuery() {
        //クエリのコンパイル待ち
        while (filterQuery == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
        return filterQuery;
    }

    private BroadcastReceiver onReloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {}
    };

    private BroadcastReceiver onActiveChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {}
    };

    @Override
    public String getStreamFilter() {
        return null;
    }

    @Override
    public void onStatus(AuthUserRecord from, final PreformedStatus status, boolean muted) {
        if (!elements.contains(status) && getFilterQuery().evaluate(status)) {
            if (muted) {
                stash.add(status);
            } else {
                final int position = prepareInsertStatus(status);
                if (position > -1) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            insertElement(status, position);
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
            case StatusManager.UPDATE_WIPE_TWEETS:
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        elements.clear();
                        notifyDataSetChanged();
                    }
                });
                stash.clear();
                break;
            case StatusManager.UPDATE_FORCE_UPDATE_UI:
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                    }
                });
                break;
            case StatusManager.UPDATE_DELETED:
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        deleteElement(status);
                    }
                });
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
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            elements.get(p).merge(status, from);
                            notifyDataSetChanged();
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
}
