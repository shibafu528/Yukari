package shibafu.yukari.common.async;

import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/03/09.
 */
public abstract class ThrowableTwitterAsyncTask<Params, Result> extends ThrowableAsyncTask<Params, Void, Result>{
    private boolean isErrored;

    @Override
    protected void onPostExecute(ThrowableResult<Result> result) {
        super.onPostExecute(result);
        checkDefaultError(result);
    }

    private void checkDefaultError(ThrowableResult<Result> result) {
        if (result == null) {
            showToast("Listの取得中にエラー");
            isErrored = true;
        }
        else if (result.isException()) {
            Exception e = result.getException();
            if (e instanceof TwitterException) {
                TwitterException te = (TwitterException) e;
                showToast(String.format("Listの取得中にエラー: %d\n%s",
                        te.getErrorCode(),
                        te.getErrorMessage()));
            }
            else {
                showToast(String.format("Listの取得中にエラー: \n%s",
                        e.getMessage()));
            }
            isErrored = true;
        }
        else {
            isErrored = false;
        }
    }

    public final boolean isErrored() {
        return this.isErrored;
    }

    protected abstract void showToast(String message);
}
