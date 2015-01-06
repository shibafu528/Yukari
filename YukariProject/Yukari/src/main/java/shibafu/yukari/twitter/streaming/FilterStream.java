package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;

/**
 * Created by shibafu on 14/02/16.
 */
public class FilterStream extends Stream{
    public static final String STREAM_TYPE = "Filter";
    public static final String EXTRA_FILTER_QUERY = "query";
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
        this.query = new ParsedQuery(query).getValidQuery();
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

    @Override
    protected String getStreamType() {
        return STREAM_TYPE;
    }

    @Override
    protected Intent createBroadcast(String action) {
        Intent intent = super.createBroadcast(action);
        intent.putExtra(EXTRA_FILTER_QUERY, query);
        return intent;
    }

    public static class ParsedQuery {
        private String validQuery;
        private String language;
        private boolean filterRetweet;

        public ParsedQuery(String query) {
            //languageの解釈
            Pattern langPattern = Pattern.compile("lang:(\\S+)");
            Matcher matcher = langPattern.matcher(query);
            if (matcher.find()) {
                query = query.replace(matcher.group(), "").trim();
                language = matcher.group(1);
            } else {
                language = null;
            }
            //-RTの解釈
            if (query.contains("-RT")) {
                query = query.replace("-RT", "").trim();
                filterRetweet = true;
            } else {
                filterRetweet = false;
            }
            validQuery = query;
        }

        public String getValidQuery() {
            return validQuery;
        }

        public String getLanguage() {
            return language;
        }

        public boolean isFilterRetweet() {
            return filterRetweet;
        }
    }
}