package shibafu.yukari.fragment.tabcontent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.view.View;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.TraceActivity;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.TabType;
import shibafu.yukari.linkage.TimelineEvent;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.MissingTwitterInstanceException;
import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/02/13.
 * @deprecated Yukari 2.1.0から {@link TimelineFragment} に移行しました。新規のコードでこのクラスを使わないでください。
 */
@Deprecated
public class DefaultTweetListFragment extends TweetListFragment {

    public static final String EXTRA_LIST_ID = "listid";

    private Status traceStart = null;
    private User targetUser = null;
    private long listId = -1;

    private LongSparseArray<Long> lastStatusIds = new LongSparseArray<>();

    private SharedPreferences preferences;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        if (context instanceof MainActivity) {
            Bundle args = getArguments();
            long id = args.getLong(EXTRA_ID);
            lastStatusIds = ((MainActivity) context).getLastStatusIdsArray(id);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();

        int mode = getMode();
        if (mode == TabType.TABTYPE_TRACE) {
            Object trace = args.getSerializable(TraceActivity.EXTRA_TRACE_START);
            if (trace instanceof PreformedStatus) {
                traceStart = (Status) trace;
                if (elements.isEmpty()) {
                    elements.add((PreformedStatus) trace);
                    notifyDataSetChanged();
                }
            }
            else if (trace instanceof Status) {
                traceStart = (Status) trace;
                if (elements.isEmpty()) {
                    elements.add(new PreformedStatus(traceStart, getCurrentUser()));
                    notifyDataSetChanged();
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
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDetach() {
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
                addLimitCount(100);
                loader.execute(loader.new Params(lastStatusIds.get(userRecord.NumericId, -1L), userRecord));
                break;
            case LOADER_LOAD_UPDATE:
                clearUnreadNotifier();
                loader.execute(loader.new Params(userRecord, true));
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        if (getMode() == TabType.TABTYPE_TRACE) {
            AsyncTask<Status, PreformedStatus, Void> task = new AsyncTask<Status, PreformedStatus, Void>() {
                @Override
                protected Void doInBackground(twitter4j.Status... params) {
                    Twitter twitter = getTwitterService().getTwitter(getCurrentUser());
                    if (twitter == null) {
                        return null;
                    }
                    twitter4j.Status status = params[0];
                    while (status.getInReplyToStatusId() > -1) {
                        try {
                            final twitter4j.Status reply = status = twitter.showStatus(status.getInReplyToStatusId());
                            final PreformedStatus ps = new PreformedStatus(reply, getCurrentUser());
                            publishProgress(ps);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    return null;
                }

                @Override
                protected void onProgressUpdate(PreformedStatus... values) {
                    insertElement(values[0]);
                }

                @Override
                protected void onPostExecute(Void result) {
                    removeFooter();
                }
            };
            task.execute(traceStart);
            changeFooterProgress(true);
        } else if (elements.isEmpty()) {
            for (AuthUserRecord user : users) {
                executeLoader(LOADER_LOAD_INIT, user);
            }
        }
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public void onTimelineEvent(@NotNull TimelineEvent event) {
        if (event instanceof TimelineEvent.Received &&
                ((TimelineEvent.Received) event).getStatus() instanceof TwitterStatus &&
                ((TwitterStatus) ((TimelineEvent.Received) event).getStatus()).getStatus() instanceof PreformedStatus) {
            TwitterStatus twitterStatus = (TwitterStatus) ((TimelineEvent.Received) event).getStatus();
            AuthUserRecord from = twitterStatus.getRepresentUser();
            PreformedStatus status = (PreformedStatus) twitterStatus.getStatus();

            if ((getMode() == TabType.TABTYPE_HOME || getMode() == TabType.TABTYPE_MENTION)
                    && users.contains(from) && !elements.contains(status)) {
                if (getMode() == TabType.TABTYPE_MENTION) {
                    boolean rtRespond = twitterStatus.getMetadata().getRepostRespondTo() != null;
                    if (rtRespond && !new NotificationType(preferences.getInt("pref_notif_respond", 0)).isEnabled()) return;
                    else if (!rtRespond && (!status.isMentionedToMe() || status.isRetweet())) return;
                }

                if (((TimelineEvent.Received) event).getMuted()) {
                    stash.add(status);
                } else {
                    getHandler().post(() -> insertElement(status));
                }
            }
        } else {
            super.onTimelineEvent(event);
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

        private boolean isNarrowMode;

        protected DefaultRESTLoader(RESTLoaderInterfaceBase loaderInterface) {
            super(loaderInterface);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isNarrowMode = sp.getBoolean("pref_narrow", false);
        }

        @Override
        protected PreformedResponseList<PreformedStatus> doInBackground(Params... params) {
            try {
                Twitter twitter = getTwitterService().getTwitterOrThrow(params[0].getUserRecord());
                ResponseList<twitter4j.Status> responseList = null;
                Paging paging = params[0].getPaging();
                if (!isNarrowMode) paging.setCount(60);
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
                        responseList = twitter.getUserListStatuses(listId, paging);
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
            } catch (MissingTwitterInstanceException e) {
                e.printStackTrace();
            } catch (TwitterException e) {
                e.printStackTrace();
                setException(e, params[0].getUserRecord());
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
