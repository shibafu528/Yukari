package shibafu.yukari.twitter.streaming;

import android.content.Context;

import shibafu.yukari.R;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by shibafu on 14/02/16.
 */
public abstract class Stream {

    private AuthUserRecord userRecord;
    protected TwitterStream stream;
    protected StreamListener listener;

    public Stream(Context context, AuthUserRecord userRecord) {
        this.userRecord = userRecord;
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setUseSSL(true);
        cb.setOAuthConsumerKey(context.getString(R.string.twitter_consumer_key));
        cb.setOAuthConsumerSecret(context.getString(R.string.twitter_consumer_secret));
        stream = new TwitterStreamFactory(cb.build()).getInstance(userRecord.getAccessToken());
    }

    public AuthUserRecord getUserRecord() {
        return userRecord;
    }

    public StreamListener getListener() {
        return listener;
    }

    public void setListener(StreamListener listener) {
        this.listener = listener;
    }

    public abstract void start();
    public abstract void stop();
}
