package info.shibafu528.gallerymultipicker;

import android.os.AsyncTask;
import android.os.Build;

import java.util.concurrent.RejectedExecutionException;

/**
 * Created by shibafu on 14/07/06.
 */
abstract class ParallelAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    public void executeParallel(Params... params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                this.executeOnExecutor(THREAD_POOL_EXECUTOR, params);
            } else {
                this.execute(params);
            }
        } catch (RejectedExecutionException e) {
            executeParallel(params);
        }
    }
}
