package shibafu.yukari.twitter;

import android.os.AsyncTask;

import java.util.List;

import shibafu.yukari.service.TwitterService;

/**
 * Created by shibafu on 14/02/13.
 */
public abstract class RESTLoader<P, T extends List<PreformedStatus>> extends AsyncTask<P, Void, T> {
    public interface RESTLoaderInterface {
        TwitterService getService();
        List<PreformedStatus> getStatuses();
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
            int position;
            for (PreformedStatus status : result) {
                AuthUserRecord checkOwn = loaderInterface.getService().isMyTweet(status);
                if (checkOwn != null) {
                    status.setReceiveUser(checkOwn);
                }
                position = loaderInterface.prepareInsertStatus(status);
                if (position > -1) {
                    dest.add(position, status);
                }
            }
            loaderInterface.notifyDataSetChanged();
        }
        loaderInterface.changeFooterProgress(false);
    }
}
