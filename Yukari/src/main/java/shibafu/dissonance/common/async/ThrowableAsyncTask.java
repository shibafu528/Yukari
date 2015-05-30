package shibafu.dissonance.common.async;

/**
 * Created by shibafu on 14/03/01.
 */
public abstract class ThrowableAsyncTask<Params, Progress, Result>
        extends ParallelAsyncTask<Params, Progress, ThrowableAsyncTask.ThrowableResult<Result>>{
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
}
