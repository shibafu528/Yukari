package shibafu.yukari.twitter.streaming;

import android.content.Context;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by shibafu on 2015/07/28.
 */
public class RestStream extends Stream {
    private final String tag;

    public RestStream(Context context, AuthUserRecord userRecord, String tag) {
        super(context, userRecord);
        this.tag = tag;
    }

    @Override
    protected String getStreamType() {
        return "Rest";
    }

    public String getTag() {
        return tag;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
