package shibafu.yukari.common.async;

import android.os.AsyncTask;
import android.os.Build;

import java.util.concurrent.RejectedExecutionException;

/**
 * Created by shibafu on 14/03/01.
 */
public abstract class ThrowableAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, ThrowableAsyncTask.ThrowableResult<Result>>{
    public static class ThrowableResult<Result> {
        private boolean isException;
        private Exception exception;
        private Result result;

        public ThrowableResult(Exception e) {
            this.exception = e;
            this.isException = true;
        }

        public ThrowableResult(Result result) {
            this.result = result;
        }

        public boolean isException() {
            return isException;
        }

        public Exception getException() {
            return exception;
        }

        public Result getResult() {
            return result;
        }
    }

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
