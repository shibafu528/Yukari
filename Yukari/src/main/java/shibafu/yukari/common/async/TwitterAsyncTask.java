package shibafu.yukari.common.async;

import android.os.AsyncTask;

import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/11/23.
 */
public abstract class TwitterAsyncTask<P> extends AsyncTask<P, Void, TwitterException> {}
