package shibafu.yukari.common.async;

import android.content.Context;

import androidx.annotation.Nullable;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/03/09.
 */
public abstract class ThrowableTwitterAsyncTask<Params, Result> extends ThrowableAsyncTask<Params, Void, Result>{
    private boolean isErrored;
    private Context context;

    public ThrowableTwitterAsyncTask() {}

    public ThrowableTwitterAsyncTask(Context context) {
        this.context = context.getApplicationContext();
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

    @Nullable
    protected Twitter getTwitterInstance(AuthUserRecord userRecord) {
        return TwitterUtil.getTwitter(context, userRecord);
    }

    public final boolean isErrored() {
        return this.isErrored;
    }

    protected abstract void showToast(String message);
}
