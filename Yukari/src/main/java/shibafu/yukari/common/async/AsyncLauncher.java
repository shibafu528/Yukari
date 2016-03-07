package shibafu.yukari.common.async;

import android.os.AsyncTask;

/**
 * Created by shibafu on 2015/08/02.
 */
public class AsyncLauncher<Param, Progress, Result> {

    public interface Task<Param, Result> {
        Result doInBackground(Param... params);
    }

    public interface Prepare {
        void onPreExecute();
    }

    public interface Done<Result> {
        void onPostExecute(Result result);
    }

    private Task<Param, Result> _task;
    private Prepare _prepare;
    private Done<Result> _done;

    public AsyncLauncher<Param, Progress, Result> task(Task<Param, Result> task) {
        _task = task;
        return this;
    }

    public AsyncLauncher<Param, Progress, Result> prepare(Prepare prepare) {
        _prepare = prepare;
        return this;
    }

    public AsyncLauncher<Param, Progress, Result> done(Done<Result> done) {
        _done = done;
        return this;
    }

    public ParallelAsyncTask<Param, Progress, Result> build() {
        return new ParallelAsyncTask<Param, Progress, Result>() {
            @Override
            protected Result doInBackground(Param... params) {
                return _task.doInBackground(params);
            }

            @Override
            protected void onPreExecute() {
                _prepare.onPreExecute();
            }

            @Override
            protected void onPostExecute(Result result) {
                _done.onPostExecute(result);
            }
        };
    }

    public AsyncTask<Param, Progress, Result> executeSerial(Param... params) {
        ParallelAsyncTask<Param, Progress, Result> task = build();
        task.execute(params);
        return task;
    }

    public AsyncTask<Param, Progress, Result> executeParallel(Param... params) {
        ParallelAsyncTask<Param, Progress, Result> task = build();
        task.executeParallel(params);
        return task;
    }
}
