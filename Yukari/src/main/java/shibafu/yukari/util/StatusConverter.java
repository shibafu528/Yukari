package shibafu.yukari.util;

import info.shibafu528.yukari.exvoice.MRuby;
import info.shibafu528.yukari.exvoice.diva.ModelFactory;
import info.shibafu528.yukari.exvoice.model.Message;
import info.shibafu528.yukari.exvoice.model.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by shibafu on 2017/06/01.
 */
public class StatusConverter {

    public static Message toMessage(MRuby mRuby, twitter4j.Status status) {
        if (status == null) {
            return null;
        }

        Map<String, Object> values = new HashMap<>();
        values.put("id", status.getId());
        values.put("message", status.getText());
        values.put("user", toUser(mRuby, status.getUser()));
        values.put("in_reply_to_user_id", status.getInReplyToUserId());
        values.put("in_reply_to_status_id", status.getInReplyToStatusId());
        values.put("retweet", toMessage(mRuby, status.getRetweetedStatus()));
        values.put("source", status.getSource());
        values.put("created", status.getCreatedAt());
        values.put("modified", status.getCreatedAt());
        return ModelFactory.newInstance(mRuby, Message.class, "Message", values);
    }

    public static User toUser(MRuby mRuby, twitter4j.User user) {
        if (user == null) {
            return null;
        }

        Map<String, Object> values = new HashMap<>();
        values.put("id", user.getId());
        values.put("idname", user.getScreenName());
        values.put("name", user.getName());
        values.put("location", user.getLocation());
        values.put("detail", user.getDescription());
        values.put("profile_image_url", user.getProfileImageURLHttps());
        values.put("url", user.getURL());
        values.put("protected", user.isProtected());
        values.put("verified", user.isVerified());
        values.put("followers_count", user.getFollowersCount());
        values.put("statuses_count", user.getStatusesCount());
        values.put("friends_count", user.getFriendsCount());
        return ModelFactory.newInstance(mRuby, User.class, "User", values);
    }

}
