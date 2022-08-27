package shibafu.yukari.common.async;

import android.os.AsyncTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.RejectedExecutionException;

/**
 * Created by shibafu on 14/07/06.
 */
public abstract class ParallelAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    public void executeParallel(Params... params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            this.executeOnExecutor(THREAD_POOL_EXECUTOR, params);
        } catch (RejectedExecutionException e) {
            executeParallel(params);
        }
    }

    /**
     * @deprecated Use Kotlin Coroutines.
     */
    @Deprecated
    public static void executeParallel(Runnable runnable) {
        new ParallelAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(@NotNull Void... params) {
                runnable.run();
                return null;
            }
        }.executeParallel();
    }

    /**
     * @deprecated Use Kotlin Coroutines.
     */
    @Deprecated
    public static void execute(@NotNull Runnable runnable) {
        new ParallelAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(@NotNull Void... params) {
                runnable.run();
                return null;
            }
        }.execute();
    }
}
