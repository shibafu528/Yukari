package shibafu.dissonance.service;

/**
 * Created by Shibafu on 13/12/22.
 */
public interface TwitterServiceDelegate {
    TwitterService getTwitterService();
    boolean isTwitterServiceBound();
}
