package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;

import java.util.ArrayList;
import java.util.Collection;
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

    private boolean isRunning = false;
    private List<ParsedQuery> queries = new ArrayList<>();
    private MutableLongSet follows = LongSets.mutable.empty();

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
        Log.d(LOG_TAG, String.format("Start FilterStream follow: %d ID(s), query: %s / user: @%s", follows.size(), getQueryString(), getUserRecord().ScreenName));
        stream.filter(new FilterQuery().track(getQueryArray()).follow(follows.toArray()));
        isRunning = true;
    }

    public void stop() {
        Log.d(LOG_TAG, String.format("Shutdown FilterStream follow: %d ID(s), query: %s / user: @%s", follows.size(), getQueryString(), getUserRecord().ScreenName));
        stream.shutdown();
        isRunning = false;
    }

    public boolean hasAnyParams() {
        return queries.size() > 0 || follows.size() > 0;
    }

    public boolean isRunning() {
        return isRunning;
    }

    //<editor-fold desc="Track">
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

        boolean result = queries.add(new ParsedQuery(query));
        restartInternal();
        return result;
    }

    public boolean removeQuery(@NonNull String query) {
        for (Iterator<ParsedQuery> iterator = queries.iterator(); iterator.hasNext(); ) {
            ParsedQuery parsedQuery = iterator.next();
            if (query.equals(parsedQuery.getQuery())) {
                iterator.remove();
                restartInternal();
                return true;
            }
        }
        return false;
    }

    public Collection<ParsedQuery> getQueries() {
        return queries;
    }

    public int getQueryCount() {
        return queries.size();
    }
    //</editor-fold>

    //<editor-fold desc="Follow">
    public void addFollowId(long id) {
        follows.add(id);
        restartInternal();
    }

    public void addFollowIds(long... id) {
        follows.addAll(id);
        restartInternal();
    }

    public void removeFollowId(long id) {
        follows.remove(id);
        restartInternal();
    }

    public void clearFollowId() {
        follows.clear();
        restartInternal();
    }

    public LongSet getFollowIds() {
        return follows;
    }
    //</editor-fold>

    @Override
    protected String getStreamType() {
        return STREAM_TYPE;
    }

    @Override
    protected Intent createBroadcast(String action) {
        Intent intent = super.createBroadcast(action);
        intent.putExtra(EXTRA_FILTER_QUERY, getQueryString());
        return intent;
    }

    private void restartInternal() {
        if (isRunning) {
            stop();
        }
        if (hasAnyParams()) {
            start();
        }
    }

    private String getQueryString() {
        StringBuilder sb = new StringBuilder();
        for (ParsedQuery query : queries) {
            if (0 < sb.length()) {
                sb.append(",");
            }
            sb.append(query.getValidQuery());
        }
        return sb.toString();
    }

    private String[] getQueryArray() {
        String[] queryArray = new String[queries.size()];
        for (int i = 0; i < queryArray.length; i++) {
            queryArray[i] = queries.get(i).getValidQuery();
        }
        return queryArray;
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