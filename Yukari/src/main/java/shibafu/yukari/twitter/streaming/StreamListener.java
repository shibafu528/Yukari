package shibafu.yukari.twitter.streaming;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;

/**
* Created by shibafu on 14/02/16.
*/
public interface StreamListener {
    void onStatus(Stream from, Status status);
    void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice);
}
