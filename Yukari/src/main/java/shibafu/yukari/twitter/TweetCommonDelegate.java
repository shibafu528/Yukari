package shibafu.yukari.twitter;

import java.util.Date;
import java.util.List;

import twitter4j.TwitterResponse;
import twitter4j.User;

/**
 * Created by shibafu on 14/03/24.
 */
public interface TweetCommonDelegate {
    int REL_OWN = 1;
    int REL_MENTION = 2;

    User getUser(TwitterResponse object);
    String getRecipientScreenName(TwitterResponse object);
    String getText(TwitterResponse object);
    Date getCreatedAt(TwitterResponse object);
    String getSource(TwitterResponse object);
    int getStatusRelation(List<AuthUserRecord> userRecords, TwitterResponse object);
}
