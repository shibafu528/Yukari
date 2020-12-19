package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.util.Log;

import shibafu.yukari.database.AuthUserRecord;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;

/**
 * Created by shibafu on 14/07/13.
 */
public class SampleStream extends Stream {
    private static final String LOG_TAG = "SampleStream";

    private StatusAdapter statusAdapter = new StatusAdapter() {
        @Override
        public void onStatus(Status status) {
            if (listener != null) listener.onStatus(SampleStream.this, status);
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            if (listener != null) listener.onDelete(SampleStream.this, statusDeletionNotice);
        }

        @Override
        public void onException(Exception e) {
            e.printStackTrace();
        }
    };

    public SampleStream(Context context, AuthUserRecord user) {
        super(context, user);
        stream.addListener(statusAdapter);
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
