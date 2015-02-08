package shibafu.yukari.twitter;

import java.util.Date;
import java.util.List;

import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.TwitterResponse;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by shibafu on 14/03/24.
 */
public class TweetCommon {

    private TweetCommon() {}

    public static TweetCommonDelegate newInstance(Class<? extends TwitterResponse> clz) {
        if (Status.class.isAssignableFrom(clz)) {
            return new StatusCommonDelegate();
        }
        else if (DirectMessage.class.isAssignableFrom(clz)) {
            return new MessageCommonDelegate();
        }
        throw new UnsupportedOperationException("対応されているクラスではありません.");
    }

    public static class StatusCommonDelegate implements TweetCommonDelegate {
        @Override
        public long getId(TwitterResponse object) {
            return ((Status) object).getId();
        }

        @Override
        public User getUser(TwitterResponse object) {
            return ((Status) object).isRetweet() ?
                    ((Status) object).getRetweetedStatus().getUser()
                    :
                    ((Status) object).getUser();
        }

        @Override
        public String getRecipientScreenName(TwitterResponse object) {
            if (object instanceof PreformedStatus) {
                return ((PreformedStatus) object).getRepresentUser().ScreenName;
            } else {
                return "";
            }
        }

        @Override
        public String getText(TwitterResponse object) {
            return ((Status) object).isRetweet() ?
                    ((Status) object).getRetweetedStatus().getText()
                    :
                    ((Status) object).getText();
        }

        @Override
        public Date getCreatedAt(TwitterResponse object) {
            return ((Status) object).getCreatedAt();
        }

        @Override
        public String getSource(TwitterResponse object) {
            return ((Status) object).getSource();
        }

        @Override
        public int getStatusRelation(List<AuthUserRecord> userRecords, TwitterResponse object) {
            for (AuthUserRecord aur : userRecords) {
                for (UserMentionEntity entity : ((Status) object).getUserMentionEntities()) {
                    if (aur.ScreenName.equals(entity.getScreenName())) {
                        return REL_MENTION;
                    }
                }
                if (aur.ScreenName.equals(((Status) object).getInReplyToScreenName())) {
                    return REL_MENTION;
                }
                if (aur.ScreenName.equals(getUser(object).getScreenName())) {
                    return REL_OWN;
                }
            }
            return 0;
        }
    }

    public static class MessageCommonDelegate implements TweetCommonDelegate {
        @Override
        public long getId(TwitterResponse object) {
            return ((DirectMessage) object).getId();
        }

        @Override
        public User getUser(TwitterResponse object) {
            return ((DirectMessage) object).getSender();
        }

        @Override
        public String getRecipientScreenName(TwitterResponse object) {
            return ((DirectMessage) object).getRecipientScreenName();
        }

        @Override
        public String getText(TwitterResponse object) {
            return ((DirectMessage) object).getText();
        }

        @Override
        public Date getCreatedAt(TwitterResponse object) {
            return ((DirectMessage) object).getCreatedAt();
        }

        @Override
        public String getSource(TwitterResponse object) {
            return "DirectMessage";
        }

        @Override
        public int getStatusRelation(List<AuthUserRecord> userRecords, TwitterResponse object) {
            for (AuthUserRecord aur : userRecords) {
                if (aur.ScreenName.equals(((DirectMessage) object).getSender().getScreenName())) {
                    return REL_OWN;
                }
            }
            return REL_MENTION;
        }
    }
}