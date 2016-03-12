package shibafu.yukari.twitter;

import android.widget.Toast;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import twitter4j.TwitterException;

import java.util.List;

/**
 * Created by shibafu on 14/02/13.
 */
public abstract class RESTLoader<P, T extends List<PreformedStatus>> extends ParallelAsyncTask<P, Void, T> {
    public interface RESTLoaderInterface {
        TwitterService getService();
        List<PreformedStatus> getStatuses();
        List<PreformedStatus> getStash();
        void notifyDataSetChanged();
        int prepareInsertStatus(PreformedStatus status);
        void changeFooterProgress(boolean isLoading);
    }

    private RESTLoaderInterface loaderInterface;
    protected TwitterException exception;
    protected AuthUserRecord exceptionUser;

    protected RESTLoader(RESTLoaderInterface loaderInterface) {
        this.loaderInterface = loaderInterface;
    }

    protected void setException(TwitterException exception, AuthUserRecord userRecord) {
        this.exception = exception;
        this.exceptionUser = userRecord;
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
                        loaderInterface.notifyDataSetChanged();
                    }
                }
                else {
                    stash.add(status);
                }

                StatusManager.getReceivedStatuses().put(status.getId(), status);
                if (loaderInterface.getService() != null && loaderInterface.getService().getStatusManager() != null) {
                    loaderInterface.getService().getStatusManager().loadQuotedEntities(status);
                }
            }
        } else if (exception != null && exceptionUser != null) {
            switch (exception.getStatusCode()) {
                case 429:
                    Toast.makeText(loaderInterface.getService().getApplicationContext(),
                            String.format("[@%s]\nレートリミット超過\n次回リセット: %d分%d秒後\n時間を空けて再度操作してください",
                                    exceptionUser.ScreenName,
                                    exception.getRateLimitStatus().getSecondsUntilReset() / 60,
                                    exception.getRateLimitStatus().getSecondsUntilReset() % 60),
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(loaderInterface.getService().getApplicationContext(),
                            String.format("[@%s]\n通信エラー: %d:%d\n%s",
                                    exceptionUser.ScreenName,
                                    exception.getStatusCode(),
                                    exception.getErrorCode(),
                                    exception.getErrorMessage()),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
        loaderInterface.changeFooterProgress(false);
    }
}
