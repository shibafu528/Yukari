package shibafu.yukari.common.async;

import android.os.AsyncTask;
import android.os.Build;

import java.util.concurrent.RejectedExecutionException;

import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/11/23.
 */
public abstract class TwitterAsyncTask<P> extends AsyncTask<P, Void, TwitterException> {
    public void executePararell(P... params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                this.executeOnExecutor(THREAD_POOL_EXECUTOR, params);
            }
            else {
                this.execute(params);
            }
        } catch (RejectedExecutionException e) {
            executePararell(params);
        }
    }
}
