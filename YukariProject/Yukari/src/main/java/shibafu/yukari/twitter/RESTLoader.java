package shibafu.yukari.twitter;

import android.os.AsyncTask;

import java.util.List;

import shibafu.yukari.common.Suppressor;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Created by shibafu on 14/02/13.
 */
public abstract class RESTLoader<P, T extends List<PreformedStatus>> extends AsyncTask<P, Void, T> {
    public interface RESTLoaderInterface {
        TwitterService getService();
        List<PreformedStatus> getStatuses();
        List<PreformedStatus> getStash();
        void notifyDataSetChanged();
        int prepareInsertStatus(PreformedStatus status);
        void changeFooterProgress(boolean isLoading);
    }

    private RESTLoaderInterface loaderInterface;

    protected RESTLoader(RESTLoaderInterface loaderInterface) {
        this.loaderInterface = loaderInterface;
    }

    @Override
    protected void onPreExecute() {
        loaderInterface.changeFooterProgress(true);
    }

    @Override
    protected void onPostExecute(T result) {
        if (result != null) {
            List<PreformedStatus> dest = loaderInterface.getStatuses();
            List<PreformedStatus> stash = loaderInterface.getStash();
            Suppressor suppressor = loaderInterface.getService().getSuppressor();
            int position;
            boolean[] mute;
            for (PreformedStatus status : result) {
                AuthUserRecord checkOwn = loaderInterface.getService().isMyTweet(status);
                if (checkOwn != null) {
                    status.setOwner(checkOwn);
                }

                mute = suppressor.decision(status);
                if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                    status.setCensoredThumbs(true);
                }

                if (!(  mute[MuteConfig.MUTE_TWEET_RTED] ||
                        (!status.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                        (status.isRetweet() && mute[MuteConfig.MUTE_RETWEET]))) {
                    position = loaderInterface.prepareInsertStatus(status);
                    if (position > -1) {
                        dest.add(position, status);
                    }
                }
                else {
                    stash.add(status);
                }

                StatusManager.getReceivedStatuses().put(status.getId(), status);
            }
            loaderInterface.notifyDataSetChanged();
        }
        loaderInterface.changeFooterProgress(false);
    }
}
