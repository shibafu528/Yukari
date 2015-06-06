package shibafu.yukari.filter.source;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedResponseList;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.rest.RESTParams;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.streaming.Stream;
import shibafu.yukari.twitter.streaming.StreamUser;

/**
 * Created by shibafu on 15/06/06.
 */
public class FilterSource {
    @Nullable
    private AuthUserRecord sourceAccount;

    @NonNull
    private Resource source;

    public FilterSource(@NonNull Resource source, @Nullable AuthUserRecord sourceAccount) {
        this.source = source;
        this.sourceAccount = sourceAccount;
    }

    @Nullable
    public AuthUserRecord getSourceAccount() {
        return sourceAccount;
    }

    @NonNull
    public Resource getSource() {
        return source;
    }

    @Nullable
    public RESTLoader<RESTParams, PreformedResponseList<PreformedStatus>> getRESTLoader(Context context, RESTLoader.RESTLoaderInterface iface) {
        return null;
    }

    @Nullable
    public Stream getStream(Context context) {
        if (sourceAccount == null) {
            return null;
        }
        return new StreamUser(context, sourceAccount);
    }

    public enum Resource {
        ALL,
        HOME,
        MENTION,
        LIST,
        SEARCH,
        TRACK,
        TRACE,
        USER,
        FAVORITE,
        BOOKMARK
    }

}
