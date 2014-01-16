package shibafu.yukari.twitter;

import java.util.ArrayList;

import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;

/**
 * Created by Shibafu on 14/01/16.
 */
public class PreformedResponseList<T> extends ArrayList<T> implements ResponseList<T> {

    private RateLimitStatus rateLimitStatus;
    private int accessLevel;

    public PreformedResponseList(ArrayList<T> preformedList, ResponseList<?> responseList) {
        if (preformedList != null && preformedList.size() > 0) {
            addAll(preformedList);
        }
        if (responseList != null) {
            this.rateLimitStatus = responseList.getRateLimitStatus();
            this.accessLevel = responseList.getAccessLevel();
        }
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return rateLimitStatus;
    }

    @Override
    public int getAccessLevel() {
        return accessLevel;
    }
}
