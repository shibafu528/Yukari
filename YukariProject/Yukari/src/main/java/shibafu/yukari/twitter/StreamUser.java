package shibafu.yukari.twitter;

import android.content.Context;
import android.util.Log;

import shibafu.yukari.R;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserStreamAdapter;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.conf.PropertyConfiguration;

/**
 * Created by Shibafu on 13/08/01.
 */
public class StreamUser {
    private static final String LOG_TAG = "StreamUser";
    private TwitterStream stream;
    private AuthUserRecord userRecord;
    private UserStreamAdapter streamAdapter = new UserStreamAdapter() {

        @Override
        public void onFavorite(User user, User user2, Status status) {
            if (listener != null) listener.onFavorite(userRecord, user, user2, status);
        }

        @Override
        public void onUnfavorite(User user, User user2, Status status) {
            if (listener != null) listener.onUnfavorite(userRecord, user, user2, status);
        }

        @Override
        public void onFollow(User user, User user2) {
            if (listener != null) listener.onFollow(userRecord, user, user2);
        }

        @Override
        public void onDirectMessage(DirectMessage directMessage) {
            if (listener != null) listener.onDirectMessage(userRecord, directMessage);
        }

        @Override
        public void onBlock(User user, User user2) {
            if (listener != null) listener.onBlock(userRecord, user, user2);
        }

        @Override
        public void onUnblock(User user, User user2) {
            if (listener != null) listener.onUnblock(userRecord, user, user2);
        }

        @Override
        public void onStatus(Status status) {
            if (listener != null) listener.onStatus(userRecord, status);
        }
    };
    private StreamListener listener = null;

    public StreamUser(Context context, AuthUserRecord user) {
        this.userRecord = user;
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setOAuthConsumerKey(context.getString(R.string.twitter_consumer_key));
        cb.setOAuthConsumerSecret(context.getString(R.string.twitter_consumer_secret));
        stream = new TwitterStreamFactory(cb.build()).getInstance(user.getAccessToken());
        stream.addListener(streamAdapter);
    }

    public void start() {
        Log.d(LOG_TAG, "Start UserStream user: @" + userRecord.ScreenName);
        stream.user();
    }

    public void stop() {
        Log.d(LOG_TAG, "Shutdown UserStream user: @" + userRecord.ScreenName);
        stream.shutdown();
    }

    public StreamListener getListener() {
        return listener;
    }

    public void setListener(StreamListener listener) {
        this.listener = listener;
    }

    public AuthUserRecord getUserRecord() {
        return userRecord;
    }

    public interface StreamListener {
        public void onFavorite(AuthUserRecord from, User user, User user2, Status status);
        public void onUnfavorite(AuthUserRecord from, User user, User user2, Status status);
        public void onFollow(AuthUserRecord from, User user, User user2);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
        public void onBlock(AuthUserRecord from, User user, User user2);
        public void onUnblock(AuthUserRecord from, User user, User user2);
        public void onStatus(AuthUserRecord from, Status status);
    }
}
