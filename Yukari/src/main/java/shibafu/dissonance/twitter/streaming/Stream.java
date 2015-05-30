package shibafu.dissonance.twitter.streaming;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import shibafu.dissonance.twitter.AuthUserRecord;
import twitter4j.ConnectionLifeCycleListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by shibafu on 14/02/16.
 */
public abstract class Stream {
    public static final String CONNECTED_STREAM = "CONNECTED_STREAM";
    public static final String DISCONNECTED_STREAM = "DISCONNECTED_STREAM";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_STREAM_TYPE = "type";

    private Context context;
    private AuthUserRecord userRecord;
    protected TwitterStream stream;
    protected StreamListener listener;

    public Stream(Context context, AuthUserRecord userRecord) {
        this.context = context;
        this.userRecord = userRecord;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.setOAuthConsumerKey(sp.getString("twitter_consumer_key", ""));
        builder.setOAuthConsumerSecret(sp.getString("twitter_consumer_secret", ""));
        stream = new TwitterStreamFactory(builder.build()).getInstance(userRecord.getAccessToken());
        stream.addConnectionLifeCycleListener(new ConnectionLifeCycleListener() {
            @Override
            public void onConnect() {
                Log.d("Stream", "Connect stream @" + Stream.this.userRecord.ScreenName);
                Stream.this.context.sendBroadcast(createBroadcast(CONNECTED_STREAM));
            }

            @Override
            public void onDisconnect() {
                Log.d("Stream", "Disconnect stream @" + Stream.this.userRecord.ScreenName);
                Stream.this.context.sendBroadcast(createBroadcast(DISCONNECTED_STREAM));
            }

            @Override
            public void onCleanUp() {
                Log.d("Stream", "CleanUp stream @" + Stream.this.userRecord.ScreenName);
            }
        });
    }

    protected Intent createBroadcast(String action) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_USER, userRecord);
        intent.putExtra(EXTRA_STREAM_TYPE, getStreamType());
        return intent;
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

    protected abstract String getStreamType();

    public abstract void start();
    public abstract void stop();
}
