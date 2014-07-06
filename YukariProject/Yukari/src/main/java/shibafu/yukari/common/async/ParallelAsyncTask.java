package shibafu.yukari.common.async;

import android.os.AsyncTask;
import android.os.Build;

import java.util.concurrent.RejectedExecutionException;

/**
 * Created by shibafu on 14/07/06.
 */
public abstract class ParallelAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    public void executeIf(Params... params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                this.executeOnExecutor(THREAD_POOL_EXECUTOR, params);
            }
            else {
                this.execute(params);
            }
        } catch (RejectedExecutionException e) {
            executeIf(params);
        }
    }
}
