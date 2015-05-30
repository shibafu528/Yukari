package shibafu.dissonance.twitter.streaming;

import android.content.Context;
import android.util.Log;

import shibafu.dissonance.twitter.AuthUserRecord;

/**
 * Created by shibafu on 14/07/13.
 */
public class SampleStream extends StreamUser {
    private static final String LOG_TAG = "SampleStream";

    public SampleStream(Context context, AuthUserRecord user) {
        super(context, user);
    }

    @Override
    public void start() {
        Log.d(LOG_TAG, "Start SampleStream user: @" + getUserRecord().ScreenName);
        stream.sample();
    }

    @Override
    public void stop() {
        Log.d(LOG_TAG, "Shutdown SampleStream user: @" + getUserRecord().ScreenName);
        stream.shutdown();
    }

    @Override
    protected String getStreamType() {
        return "Sample";
    }
}
