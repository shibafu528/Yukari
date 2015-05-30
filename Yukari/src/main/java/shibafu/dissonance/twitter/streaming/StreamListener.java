package shibafu.dissonance.twitter.streaming;

import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.User;

/**
* Created by shibafu on 14/02/16.
*/
public interface StreamListener {
    public void onFavorite      (Stream from, User user, User user2, Status status);
    public void onUnfavorite    (Stream from, User user, User user2, Status status);
    public void onFollow        (Stream from, User user, User user2);
    public void onDirectMessage (Stream from, DirectMessage directMessage);
    public void onBlock         (Stream from, User user, User user2);
    public void onUnblock       (Stream from, User user, User user2);
    public void onStatus        (Stream from, Status status);
    public void onDelete        (Stream from, StatusDeletionNotice statusDeletionNotice);
    public void onDeletionNotice(Stream from, long directMessageId, long userId);
}
