package shibafu.yukari.common;

import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/09/01.
 */
public interface AttachableList {
    String getTitle();
    AuthUserRecord getCurrentUser();
    void scrollToTop();
    void scrollToBottom();
}
