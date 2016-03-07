package shibafu.yukari.twitter;

import twitter4j.TwitterException;

/**
 * Created by shibafu on 2016/03/06.
 */
public class MissingTwitterInstanceException extends TwitterException {
    public MissingTwitterInstanceException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingTwitterInstanceException(String message) {
        super(message);
    }

    public MissingTwitterInstanceException(Exception cause) {
        super(cause);
    }
}
