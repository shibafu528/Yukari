package shibafu.yukari.twitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.util.MorseCodec;
import shibafu.util.TweetImageUrl;
import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.Place;
import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.SymbolEntity;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/10/13.
 */
public class PreformedStatus implements Status{
    private final static Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");

    private Status status;
    private String text;
    private String plainSource;
    private List<String> mediaLinkList;

    private AuthUserRecord receiveUser;

    public PreformedStatus(Status status, AuthUserRecord receiveUser) {
        this.status = status;
        this.receiveUser = receiveUser;

        //全Entity置き換え、モールスの復号を行う
        text = MorseCodec.decode(replaceAllEntities(status));
        //via抽出
        Matcher matcher = VIA_PATTERN.matcher(status.getSource());
        if (matcher.find()) {
            plainSource = matcher.group(1);
        }
        else {
            plainSource = status.getSource();
        }
        //メディアリンクリストを作成
        mediaLinkList = new ArrayList<String>();
        for (URLEntity urlEntity : status.getURLEntities()) {
            String expanded = TweetImageUrl.getFullImageUrl(urlEntity.getExpandedURL());
            if (expanded != null) {
                mediaLinkList.add(expanded);
            }
        }
        for (MediaEntity mediaEntity : status.getMediaEntities()) {
            mediaLinkList.add(mediaEntity.getMediaURL());
        }
    }

    private static String replaceAllEntities(Status status) {
        String text = status.getText();
        for (URLEntity e : status.getURLEntities()) {
            text = text.replace(e.getURL(), e.getExpandedURL());
        }
        for (MediaEntity e : status.getMediaEntities()) {
            text = text.replace(e.getURL(), e.getMediaURL());
        }
        return text;
    }

    @Override
    public Date getCreatedAt() {
        return status.getCreatedAt();
    }

    @Override
    public long getId() {
        return status.getId();
    }

    @Override
    public String getText() {
        return text;
    }

    public String getPlainText() {
        return status.getText();
    }

    @Override
    public String getSource() {
        return plainSource;
    }

    public String getFullSource() {
        return status.getSource();
    }

    @Override
    public boolean isTruncated() {
        return status.isTruncated();
    }

    @Override
    public long getInReplyToStatusId() {
        return status.getInReplyToStatusId();
    }

    @Override
    public long getInReplyToUserId() {
        return status.getInReplyToUserId();
    }

    @Override
    public String getInReplyToScreenName() {
        return status.getInReplyToScreenName();
    }

    @Override
    public GeoLocation getGeoLocation() {
        return status.getGeoLocation();
    }

    @Override
    public Place getPlace() {
        return status.getPlace();
    }

    @Override
    public boolean isFavorited() {
        return status.isFavorited();
    }

    @Override
    public boolean isRetweeted() {
        return status.isRetweeted();
    }

    @Override
    public int getFavoriteCount() {
        return status.getFavoriteCount();
    }

    @Override
    public User getUser() {
        return status.getUser();
    }

    public AuthUserRecord getReceiveUser() {
        return receiveUser;
    }

    @Override
    public boolean isRetweet() {
        return status.isRetweet();
    }

    @Override
    public Status getRetweetedStatus() {
        return status.getRetweetedStatus();
    }

    @Override
    public long[] getContributors() {
        return status.getContributors();
    }

    @Override
    public int getRetweetCount() {
        return status.getRetweetCount();
    }

    @Override
    public boolean isRetweetedByMe() {
        return status.isRetweetedByMe();
    }

    @Override
    public long getCurrentUserRetweetId() {
        return status.getCurrentUserRetweetId();
    }

    @Override
    public boolean isPossiblySensitive() {
        return status.isPossiblySensitive();
    }

    @Override
    public String getIsoLanguageCode() {
        return status.getIsoLanguageCode();
    }

    @Override
    public int compareTo(Status another) {
        return status.compareTo(another);
    }

    @Override
    public UserMentionEntity[] getUserMentionEntities() {
        return status.getUserMentionEntities();
    }

    @Override
    public URLEntity[] getURLEntities() {
        return status.getURLEntities();
    }

    @Override
    public HashtagEntity[] getHashtagEntities() {
        return status.getHashtagEntities();
    }

    @Override
    public MediaEntity[] getMediaEntities() {
        return status.getMediaEntities();
    }

    @Override
    public SymbolEntity[] getSymbolEntities() {
        return status.getSymbolEntities();
    }

    public List<String> getMediaLinkList() {
        return mediaLinkList;
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return status.getRateLimitStatus();
    }

    @Override
    public int getAccessLevel() {
        return status.getAccessLevel();
    }
}
