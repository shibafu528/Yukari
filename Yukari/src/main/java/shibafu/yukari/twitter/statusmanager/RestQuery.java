package shibafu.yukari.twitter.statusmanager;

import org.jetbrains.annotations.NotNull;
import twitter4j.*;

/**
 * Created by shibafu on 2015/07/28.
 */
public interface RestQuery {
    ResponseList<Status> getRestResponses(@NotNull Twitter twitter, @NotNull Paging paging) throws TwitterException;
}
