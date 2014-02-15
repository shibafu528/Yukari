package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.util.Log;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;

/**
 * Created by shibafu on 14/02/16.
 */
public class FilterStream extends Stream{
    private static final String LOG_TAG = "FilterStream";
    private final String query;
    private StatusAdapter statusListener = new StatusAdapter() {
        @Override
        public void onStatus(Status status) {
            if (listener != null) listener.onStatus(FilterStream.this, status);
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            if (listener != null) listener.onDelete(FilterStream.this, statusDeletionNotice);
        }

        @Override
        public void onException(Exception e) {
            e.printStackTrace();
        }
    };

    public FilterStream(Context context, AuthUserRecord userRecord, String query) {
        super(context, userRecord);
        this.query = query;
        stream.addListener(statusListener);
    }

    public void start() {
        Log.d(LOG_TAG, String.format("Start FilterStream query: %s / user: @%s", query, getUserRecord().ScreenName));
        stream.filter(new FilterQuery().track(new String[]{query}));
    }

    public void stop() {
        Log.d(LOG_TAG, String.format("Shutdown FilterStream query: %s / user: @%s", query, getUserRecord().ScreenName));
        stream.shutdown();
    }

    public String getQuery() {
        return query;
    }
}
