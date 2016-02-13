package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shibafu on 14/02/16.
 */
public class FilterStream extends Stream{
    public static final String STREAM_TYPE = "Filter";
    public static final String EXTRA_FILTER_QUERY = "query";

    private static final String LOG_TAG = "FilterStream";
    private static final Map<AuthUserRecord, FilterStream> instances = new HashMap<>();

    private List<ParsedQuery> queries = new ArrayList<>();
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

    @NonNull
    public static FilterStream getInstance(Context context, AuthUserRecord userRecord) {
        if (!instances.containsKey(userRecord)) {
            instances.put(userRecord, new FilterStream(context, userRecord));
        }
        return instances.get(userRecord);
    }

    private FilterStream(Context context, AuthUserRecord userRecord) {
        super(context, userRecord);
        stream.addListener(statusListener);
    }

    public void start() {
        Log.d(LOG_TAG, String.format("Start FilterStream query: %s / user: @%s", getQuery(), getUserRecord().ScreenName));
        stream.filter(new FilterQuery().track(getQueryArray()));
    }

    public void stop() {
        Log.d(LOG_TAG, String.format("Shutdown FilterStream query: %s / user: @%s", getQuery(), getUserRecord().ScreenName));
        stream.shutdown();
    }

    public String getQuery() {
        StringBuilder sb = new StringBuilder();
        for (ParsedQuery query : queries) {
            if (0 < sb.length()) {
                sb.append(",");
            }
            sb.append(query.getValidQuery());
        }
        return sb.toString();
    }

    public String[] getQueryArray() {
        String[] queryArray = new String[queries.size()];
        for (int i = 0; i < queryArray.length; i++) {
            queryArray[i] = queries.get(i).getValidQuery();
        }
        return queryArray;
    }

    public boolean contains(String query) {
        if (query == null) return false;

        for (ParsedQuery parsedQuery : queries) {
            if (query.equals(parsedQuery.getQuery())) {
                return true;
            }
        }
        return false;
    }

    public boolean addQuery(@NonNull String query) {
        if (contains(query)) {
            return false;
        }

        return queries.add(new ParsedQuery(query));
    }

    public boolean removeQuery(@NonNull String query) {
        for (Iterator<ParsedQuery> iterator = queries.iterator(); iterator.hasNext(); ) {
            ParsedQuery parsedQuery = iterator.next();
            if (query.equals(parsedQuery.getQuery())) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public int getQueryCount() {
        return queries.size();
    }

    @Override
    protected String getStreamType() {
        return STREAM_TYPE;
    }

    @Override
    protected Intent createBroadcast(String action) {
        Intent intent = super.createBroadcast(action);
        intent.putExtra(EXTRA_FILTER_QUERY, getQuery());
        return intent;
    }

    public static class ParsedQuery implements Parcelable {
        private String query;
        private String validQuery;
        private String language;
        private boolean filterRetweet;

        public ParsedQuery(String query) {
            this.query = query;

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

        protected ParsedQuery(Parcel in) {
            query = in.readString();
            validQuery = in.readString();
            language = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(query);
            dest.writeString(validQuery);
            dest.writeString(language);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ParsedQuery> CREATOR = new Creator<ParsedQuery>() {
            @Override
            public ParsedQuery createFromParcel(Parcel in) {
                return new ParsedQuery(in);
            }

            @Override
            public ParsedQuery[] newArray(int size) {
                return new ParsedQuery[size];
            }
        };

        public String getQuery() {
            return query;
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