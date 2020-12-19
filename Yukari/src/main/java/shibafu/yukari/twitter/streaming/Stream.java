package shibafu.yukari.twitter.streaming;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.database.AuthUserRecord;
import twitter4j.ConnectionLifeCycleListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by shibafu on 14/02/16.
 */
public abstract class Stream {
    private Context context;
    private AuthUserRecord userRecord;
    protected TwitterStream stream;
    protected StreamListener listener;

    public Stream(Context context, AuthUserRecord userRecord) {
        this.context = context;
        this.userRecord = userRecord;
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setOAuthConsumerKey(context.getString(R.string.twitter_consumer_key));
        cb.setOAuthConsumerSecret(context.getString(R.string.twitter_consumer_secret));
        stream = new TwitterStreamFactory(cb.build()).getInstance(userRecord.getTwitterAccessToken());
        stream.addConnectionLifeCycleListener(new ConnectionLifeCycleListener() {
            @Override
            public void onConnect() {
                Log.d("Stream", "Connect stream @" + Stream.this.userRecord.ScreenName);
                Stream.this.context.sendBroadcast(createBroadcast(TwitterService.ACTION_STREAM_CONNECTED));
            }

            @Override
            public void onDisconnect() {
                Log.d("Stream", "Disconnect stream @" + Stream.this.userRecord.ScreenName);
                Stream.this.context.sendBroadcast(createBroadcast(TwitterService.ACTION_STREAM_CONNECTED));
            }

            @Override
            public void onCleanUp() {
                Log.d("Stream", "CleanUp stream @" + Stream.this.userRecord.ScreenName);
            }
        });
    }

    protected Intent createBroadcast(String action) {
        Intent intent = new Intent(action);
        intent.putExtra(TwitterService.EXTRA_USER, userRecord);
        intent.putExtra(TwitterService.EXTRA_CHANNEL_ID, getStreamType());
        intent.putExtra(TwitterService.EXTRA_CHANNEL_NAME, getStreamType() + "Stream");
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
