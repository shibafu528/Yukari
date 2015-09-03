package shibafu.yukari.twitter.statusmanager;

import twitter4j.*;

/**
 * Created by shibafu on 2015/07/28.
 */
public interface RestQuery {
    ResponseList<Status> getRestResponses(Twitter twitter, Paging paging) throws TwitterException;
}
