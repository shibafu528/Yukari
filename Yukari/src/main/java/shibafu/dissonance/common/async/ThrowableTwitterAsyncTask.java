package shibafu.dissonance.common.async;

import shibafu.dissonance.service.TwitterServiceDelegate;
import shibafu.dissonance.twitter.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/03/09.
 */
public abstract class ThrowableTwitterAsyncTask<Params, Result> extends ThrowableAsyncTask<Params, Void, Result>{
    private boolean isErrored;
    private TwitterServiceDelegate delegate;

    public ThrowableTwitterAsyncTask() {}

    public ThrowableTwitterAsyncTask(TwitterServiceDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onPostExecute(ThrowableResult<Result> result) {
        super.onPostExecute(result);
        checkDefaultError(result);
    }

    private void checkDefaultError(ThrowableResult<Result> result) {
        if (result == null) {
            showToast("エラーが発生しました");
            isErrored = true;
        }
        else if (result.isException()) {
            Exception e = result.getException();
            if (e instanceof TwitterException) {
                TwitterException te = (TwitterException) e;
                showToast(String.format("通信エラー: %d\n%s",
                        te.getErrorCode(),
                        te.getErrorMessage()));
            }
            else {
                showToast(String.format("エラーが発生しました: \n%s",
                        e.getMessage()));
            }
            isErrored = true;
        }
        else {
            isErrored = false;
        }
    }

    protected Twitter getTwitterInstance() {
        return delegate.getTwitterService().getTwitter();
    }

    protected Twitter getTwitterInstance(AuthUserRecord userRecord) {
        Twitter twitter = delegate.getTwitterService().getTwitter();
        twitter.setOAuthAccessToken(userRecord.getAccessToken());
        return twitter;
    }

    public final boolean isErrored() {
        return this.isErrored;
    }

    protected abstract void showToast(String message);
}
