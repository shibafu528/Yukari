package shibafu.dissonance.twitter.streaming;

import android.content.Context;
import android.util.Log;

import shibafu.dissonance.twitter.AuthUserRecord;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.User;
import twitter4j.UserStreamAdapter;

/**
 * Created by Shibafu on 13/08/01.
 */
public class StreamUser extends Stream{
    public static final String STREAM_TYPE = "User";
    private static final String LOG_TAG = "StreamUser";
    private UserStreamAdapter streamAdapter = new UserStreamAdapter() {

        @Override
        public void onFavorite(User user, User user2, Status status) {
            if (listener != null) listener.onFavorite(StreamUser.this, user, user2, status);
        }

        @Override
        public void onUnfavorite(User user, User user2, Status status) {
            if (listener != null) listener.onUnfavorite(StreamUser.this, user, user2, status);
        }

        @Override
        public void onFollow(User user, User user2) {
            if (listener != null) listener.onFollow(StreamUser.this, user, user2);
        }

        @Override
        public void onDirectMessage(DirectMessage directMessage) {
            if (listener != null) listener.onDirectMessage(StreamUser.this, directMessage);
        }

        @Override
        public void onBlock(User user, User user2) {
            if (listener != null) listener.onBlock(StreamUser.this, user, user2);
        }

        @Override
        public void onUnblock(User user, User user2) {
            if (listener != null) listener.onUnblock(StreamUser.this, user, user2);
        }

        @Override
        public void onStatus(Status status) {
            if (listener != null) listener.onStatus(StreamUser.this, status);
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            if (listener != null) listener.onDelete(StreamUser.this, statusDeletionNotice);
        }

        @Override
        public void onDeletionNotice(long directMessageId, long userId) {
            if (listener != null) listener.onDeletionNotice(StreamUser.this, directMessageId, userId);
        }

        @Override
        public void onException(Exception ex) {
            ex.printStackTrace();
        }
    };

    public StreamUser(Context context, AuthUserRecord user) {
        super(context, user);
        stream.addListener(streamAdapter);
    }

    public void start() {
        Log.d(LOG_TAG, "Start UserStream user: @" + getUserRecord().ScreenName);
        stream.user();
    }

    public void stop() {
        Log.d(LOG_TAG, "Shutdown UserStream user: @" + getUserRecord().ScreenName);
        stream.shutdown();
    }

    @Override
    protected String getStreamType() {
        return STREAM_TYPE;
    }
}
