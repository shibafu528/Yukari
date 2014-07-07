package shibafu.yukari.common.async;

import android.content.Context;
import android.widget.Toast;

import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/11/23.
 */
public abstract class TwitterAsyncTask<P> extends ParallelAsyncTask<P, Void, TwitterException> {
    private Context context;

    protected TwitterAsyncTask(Context context) {
        this.context = context;
    }

    @Override
    protected void onPostExecute(TwitterException e) {
        super.onPostExecute(e);
        if (e != null) {
            Toast.makeText(context,
                    String.format("通信エラー: %d\n%s",
                            e.getErrorCode(),
                            e.getErrorMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
