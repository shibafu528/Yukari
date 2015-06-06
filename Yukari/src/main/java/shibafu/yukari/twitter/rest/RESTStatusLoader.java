package shibafu.yukari.twitter.rest;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.lang.ref.WeakReference;

import shibafu.yukari.twitter.PRListFactory;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 15/06/06.
 */
public abstract class RESTStatusLoader
        extends RESTLoader<RESTParams, PreformedResponseList<PreformedStatus>> {

    private boolean isNarrowMode;
    private WeakReference<Context> context;

    protected RESTStatusLoader(Context context, RESTLoaderInterface loaderInterface) {
        super(loaderInterface);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        isNarrowMode = sp.getBoolean("pref_narrow", false);
        this.context = new WeakReference<>(context);
    }

    @Override
    protected PreformedResponseList<PreformedStatus> doInBackground(RESTParams... params) {
        Twitter twitter = params[0].getUserRecord().getTwitterInstance(context.get());
        twitter.setOAuthAccessToken(params[0].getUserRecord().getAccessToken());
        try {
            Paging paging = params[0].getPaging();
            if (!isNarrowMode) paging.setCount(60);
            ResponseList<twitter4j.Status> responseList = getStatuses(twitter, paging);
//                switch (getSource()) {
//                    case HOME:
//                        responseList = twitter.getHomeTimeline(paging);
//                        break;
//                    case MENTION:
//                        responseList = twitter.getMentionsTimeline(paging);
//                        break;
//                    case USER:
//                        responseList = twitter.getUserTimeline(targetUser.getId(), paging);
//                        break;
//                    case FAVORITE:
//                        responseList = twitter.getFavorites(targetUser.getId(), paging);
//                        break;
//                    case LIST:
//                        responseList = twitter.getUserListStatuses(listId, paging);
//                        break;
//                }
            if (!params[0].isSaveLastPaging()) {
                LoadMarkerStatus markerStatus;

                if (responseList == null || responseList.isEmpty()) {
                    markerStatus = new LoadMarkerStatus(
                            params[0].getPaging().getMaxId(),
                            params[0].getUserRecord().NumericId);
                    return PRListFactory.create(markerStatus, params[0].getUserRecord());
                } else {
                    markerStatus = new LoadMarkerStatus(
                            responseList.get(responseList.size() - 1).getId(),
                            params[0].getUserRecord().NumericId);
                    responseList.add(markerStatus);
                }
            }
            return PRListFactory.create(responseList, params[0].getUserRecord());
        } catch (TwitterException e) {
            e.printStackTrace();
            setException(e, params[0].getUserRecord());
        }
        return null;
    }

    protected abstract ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging) throws TwitterException;
}
