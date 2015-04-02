package shibafu.yukari.twitter.statusimpl;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.LinkMediaFactory;
import shibafu.yukari.media.TwitterVideo;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.MorseCodec;
import twitter4j.ExtendedMediaEntity;
import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.Place;
import twitter4j.RateLimitStatus;
import twitter4j.Scopes;
import twitter4j.Status;
import twitter4j.SymbolEntity;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/10/13.
 */
public class PreformedStatus implements Status{
    private final static int TOO_MANY_REPEAT = 3;
    private final static Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");
    private final static Pattern STATUS_PATTERN = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)$");

    //基本ステータスデータ
    private Status status;
    private PreformedStatus retweetedStatus;

    //基本は最初の受信時に一度だけ加工しておく情報
    private String text;
    private String plainSource;
    private List<LinkMedia> mediaLinkList;
    private URLEntity[] urlEntities;
    private List<Long> quoteEntities;
    private boolean isMentionedToMe;
    private boolean censoredThumbs;
    private boolean isTooManyRepeatText;
    private String repeatedSequence;

    //受信の度に変動する情報
    private int favoriteCount;
    private int retweetCount;
    private RateLimitStatus rateLimitStatus;
    private HashMapEx<Long, Boolean> isFavorited = new HashMapEx<>();
    private HashMapEx<Long, Boolean> isRetweeted = new HashMapEx<>();
    private HashMapEx<Long, Boolean> isMentioned = new HashMapEx<>();

    //代表ユーザ
    private AuthUserRecord representUser;
    //受信したユーザのList
    private List<AuthUserRecord> receivedUsers = new ArrayList<>();
    private List<Long> receivedIds = new ArrayList<>();

    public PreformedStatus(Status status, AuthUserRecord receivedUser) {
        this.status = status;
        this.representUser = receivedUser;
        receivedUsers.add(receivedUser);

        //全Entity置き換え、モールスの復号を行う
        text = MorseCodec.decode(replaceAllEntities(status));
        //繰り返し文かどうかチェック
        {
            int maxRepeat = 0;
            String[] split = text.split("\n");
            for (String s1 : split) {
                int repeat = 0;
                for (String s2 : split) {
                    if (!TextUtils.isEmpty(s2.trim()) && s1.equals(s2)) {
                        ++repeat;
                    }
                }
                if ((maxRepeat = Math.max(maxRepeat, repeat)) == repeat) {
                    repeatedSequence = s1;
                }
            }
            if (maxRepeat > TOO_MANY_REPEAT) {
                isTooManyRepeatText = true;
            }
        }
        //via抽出
        Matcher matcher = VIA_PATTERN.matcher(status.getSource());
        if (matcher.find()) {
            plainSource = matcher.group(1);
        }
        else {
            plainSource = status.getSource();
        }
        //リンクリストを作成
        ArrayList<URLEntity> urlEntities = new ArrayList<>();
        mediaLinkList = new ArrayList<LinkMedia>() {
            @Override
            public boolean add(LinkMedia object) {
                return !this.contains(object) && super.add(object);
            }
        };
        quoteEntities = new ArrayList<>();
        for (URLEntity urlEntity : status.getURLEntities()) {
            LinkMedia media = LinkMediaFactory.newInstance(urlEntity.getExpandedURL());
            if (media != null) {
                mediaLinkList.add(media);
            }
            else {
                urlEntities.add(urlEntity);
            }

            Matcher m = STATUS_PATTERN.matcher(urlEntity.getExpandedURL());
            if (m.find()) {
                quoteEntities.add(Long.valueOf(m.group(1)));
            }
        }
        this.urlEntities = urlEntities.toArray(new URLEntity[urlEntities.size()]);
        for (MediaEntity mediaEntity : status.getMediaEntities()) {
            mediaLinkList.add(LinkMediaFactory.newInstance(mediaEntity.getMediaURLHttps()));
        }
        for (ExtendedMediaEntity mediaEntity : status.getExtendedMediaEntities()) {
            switch (mediaEntity.getType()) {
                case "video":
                case "animated_gif":
                    if (mediaEntity.getVideoVariants().length > 0) {
                        ExtendedMediaEntity.Variant variant = mediaEntity.getVideoVariants()[0];
                        for (Iterator<LinkMedia> iterator = mediaLinkList.iterator(); iterator.hasNext(); ) {
                            if (iterator.next().getBrowseURL().equals(mediaEntity.getMediaURLHttps())) {
                                iterator.remove();
                            }
                        }
                        mediaLinkList.add(new TwitterVideo(variant.getUrl(), mediaEntity.getMediaURLHttps()));
                    }
                    break;
                default:
                    mediaLinkList.add(LinkMediaFactory.newInstance(mediaEntity.getMediaURLHttps()));
                    break;
            }
        }
        //RTステータスならそちらも生成
        if (isRetweet()) {
            retweetedStatus = new PreformedStatus(status.getRetweetedStatus(), receivedUser);
        }
        merge(status, receivedUser);
    }

    public PreformedStatus(PreformedStatus other) {
        this.status = other.status;
        this.retweetedStatus = other.retweetedStatus;
        this.text = other.text;
        this.plainSource = other.plainSource;
        this.mediaLinkList = other.mediaLinkList;
        this.urlEntities = other.urlEntities;
        this.quoteEntities = other.quoteEntities;
        this.isMentionedToMe = other.isMentionedToMe;
        this.censoredThumbs = other.censoredThumbs;
        this.favoriteCount = other.favoriteCount;
        this.retweetCount = other.retweetCount;
        this.rateLimitStatus = other.rateLimitStatus;
        this.isFavorited = other.isFavorited;
        this.isRetweeted = other.isRetweeted;
        this.isMentioned = other.isMentioned;
        this.representUser = other.representUser;
        this.receivedUsers = other.receivedUsers;
        this.receivedIds = other.receivedIds;
    }

    public void merge(Status status, AuthUserRecord receivedUser) {
        if (status instanceof FakeStatus) {
            merge((FakeStatus) status, receivedUser);
            return;
        }

        favoriteCount = status.getFavoriteCount();
        retweetCount = status.getRetweetCount();
        rateLimitStatus = status.getRateLimitStatus();
        //extended_entitiesを後から取得できた場合
        if (this.status.getExtendedMediaEntities().length < status.getExtendedMediaEntities().length
                && status.getExtendedMediaEntities().length > 1) {
            for (MediaEntity mediaEntity : status.getExtendedMediaEntities()) {
                mediaLinkList.add(LinkMediaFactory.newInstance(mediaEntity.getMediaURLHttps()));
            }
        }
        if (receivedUser != null) {
            addReceivedUserIfNotExist(receivedUser);
            isFavorited.put(receivedUser.NumericId, status.isFavorited());
            isRetweeted.put(receivedUser.NumericId, status.isRetweeted());
            //メンション判定
            for (UserMentionEntity entity : status.getUserMentionEntities()) {
                if (entity.getId() == receivedUser.NumericId) {
                    isMentionedToMe = true;
                    isMentioned.put(receivedUser.NumericId, true);
                    break;
                }
            }
            //代表ユーザ判定
            // Owner > Mentioned > Primary > Other
            if (status.getUser().getId() == receivedUser.NumericId
                    || isMentioned.get(receivedUser.NumericId, false)
                    || !isMentionedToMe && !representUser.isPrimary) {
                representUser = receivedUser;
            }
        }
        if (retweetedStatus != null && status.isRetweeted()) {
            retweetedStatus.merge(status.getRetweetedStatus(), receivedUser);
        }
    }

    public void merge(PreformedStatus status) {
        favoriteCount = status.getFavoriteCount();
        retweetCount = status.getRetweetCount();
        rateLimitStatus = status.getRateLimitStatus();
        //extended_entitiesを後から取得できた場合
        if (this.status.getExtendedMediaEntities().length < status.getExtendedMediaEntities().length
                && status.getExtendedMediaEntities().length > 1) {
            for (MediaEntity mediaEntity : status.getExtendedMediaEntities()) {
                mediaLinkList.add(LinkMediaFactory.newInstance(mediaEntity.getMediaURLHttps()));
            }
        }
        for (AuthUserRecord userRecord : status.getReceivedUsers()) {
            addReceivedUserIfNotExist(userRecord);
            isFavorited.put(userRecord.NumericId, status.isFavoritedBy(userRecord));
            isRetweeted.put(userRecord.NumericId, status.isRetweetedBy(userRecord));
            //メンション判定
            if (status.isMentionedTo(userRecord.NumericId)) {
                isMentionedToMe = true;
                isMentioned.put(userRecord.NumericId, true);
            }
        }
        //代表ユーザ判定
        // Owner > Mentioned > Primary > Other
        long representId = status.getRepresentUser().NumericId;
        if (status.getUser().getId() == representId
                || isMentioned.get(representId, false)
                || !isMentionedToMe && !representUser.isPrimary) {
            representUser = status.getRepresentUser();
        }
        if (retweetedStatus != null && status.isRetweeted()) {
            retweetedStatus.merge(status.getRetweetedStatus());
        }
    }

    public void merge(FakeStatus status, AuthUserRecord receivedUser) {
        if (status instanceof FavFakeStatus && receivedUser != null) {
            if (status.getUser().getId() == receivedUser.NumericId) {
                addReceivedUserIfNotExist(receivedUser);
            }
            isFavorited.put(status.getUser().getId(), status.isFavorited());
        }
    }

    //外部でオーナー判定した時に代表ユーザを上書きするためのメソッド
    public void setOwner(AuthUserRecord ownerUser) {
        if (status.getUser().getId() == ownerUser.NumericId) {
            representUser = ownerUser;
            addReceivedUserIfNotExist(ownerUser);
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

    protected Status getStatus() {
        return status;
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

    public boolean isTooManyRepeatText() {
        return retweetedStatus != null ? retweetedStatus.isTooManyRepeatText() : isTooManyRepeatText;
    }

    public String getRepeatedSequence() {
        return retweetedStatus != null ? retweetedStatus.getRepeatedSequence() : repeatedSequence;
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
    public int getFavoriteCount() {
        return favoriteCount;
    }

    @Override
    public User getUser() {
        return status.getUser();
    }

    public User getSourceUser() {
        return status.isRetweet()? retweetedStatus.getUser() : status.getUser();
    }

    @Override
    public boolean isRetweet() {
        return status.isRetweet();
    }

    @Override
    public PreformedStatus getRetweetedStatus() {
        return retweetedStatus;
    }

    @Override
    public long[] getContributors() {
        return status.getContributors();
    }

    @Override
    public int getRetweetCount() {
        return retweetCount;
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
    public String getLang() {
        return status.getLang();
    }

    @Override
    public Scopes getScopes() {
        return status.getScopes();
    }

    @Override
    public String[] getWithheldInCountries() {
        return status.getWithheldInCountries();
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
        return urlEntities;
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
    public ExtendedMediaEntity[] getExtendedMediaEntities() {
        return status.getExtendedMediaEntities();
    }

    @Override
    public SymbolEntity[] getSymbolEntities() {
        return status.getSymbolEntities();
    }

    public List<LinkMedia> getMediaLinkList() {
        return mediaLinkList;
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return rateLimitStatus;
    }

    @Override
    public int getAccessLevel() {
        return status.getAccessLevel();
    }

    @Override
    public boolean isFavorited() {
        return isFavoritedBy(representUser);
    }

    @Override
    public boolean isRetweeted() {
        return isRetweetedBy(representUser);
    }

    public boolean isFavoritedBy(AuthUserRecord userRecord) {
        return isFavorited.get(userRecord.NumericId, false);
    }

    public boolean isFavoritedBy(long id) {
        return isFavorited.get(id, false);
    }

    public boolean isFavoritedSomeone() {
        return isFavorited.containsWithFilter(receivedIds, true);
    }

    public List<AuthUserRecord> getFavoritedAccounts() {
        List<Long> ids = isFavorited.filterByKey(receivedIds).filterByValue(true);
        List<AuthUserRecord> userRecords = new ArrayList<>();
        for (Long id : ids) {
            for (AuthUserRecord receivedUser : receivedUsers) {
                if (receivedUser.NumericId == id) {
                    userRecords.add(receivedUser);
                }
            }
        }
        return userRecords;
    }

    public boolean isRetweetedBy(AuthUserRecord userRecord) {
        return isRetweeted.get(userRecord.NumericId, false);
    }

    public boolean isRetweetedBy(long id) {
        return isRetweeted.get(id, false);
    }

    public boolean isRetweetedSomeone() {
        return isRetweeted.containsWithFilter(receivedIds, true);
    }

    public boolean isMentionedToMe() {
        return isMentionedToMe;
    }

    public boolean isMentionedTo(AuthUserRecord userRecord) {
        return isMentioned.get(userRecord.NumericId, false);
    }

    public boolean isMentionedTo(long id) {
        return isMentioned.get(id, false);
    }

    public boolean isCensoredThumbs() {
        return censoredThumbs;
    }

    public void setCensoredThumbs(boolean censoredThumbs) {
        this.censoredThumbs = censoredThumbs;
    }

    public AuthUserRecord getRepresentUser() {
        return representUser;
    }

    public List<AuthUserRecord> getReceivedUsers() {
        return receivedUsers;
    }

    private void addReceivedUserIfNotExist(AuthUserRecord userRecord) {
        if (!receivedUsers.contains(userRecord)) {
            receivedUsers.add(userRecord);
        }
        if (!receivedIds.contains(userRecord.NumericId)) {
            receivedIds.add(userRecord.NumericId);
        }
    }

    public List<Long> getQuoteEntities() {
        return quoteEntities;
    }

    private class HashMapEx<K, V> extends HashMap<K, V> {
        public V get(K key, V ifNotExistValue) {
            V val = get(key);
            if (val == null) return ifNotExistValue;
            else return val;
        }

        public boolean containsWithFilter(Collection<K> keyCollection, V value) {
            for (Entry<K, V> kvEntry : this.entrySet()) {
                if (keyCollection.contains(kvEntry.getKey()) && kvEntry.getValue().equals(value)) {
                    return true;
                }
            }
            return false;
        }

        public HashMapEx<K, V> filterByKey(Collection<K> keyCollection) {
            HashMapEx<K, V> mapEx = new HashMapEx<>();
            for (Entry<K, V> kvEntry : this.entrySet()) {
                if (keyCollection.contains(kvEntry.getKey())) {
                    mapEx.put(kvEntry.getKey(), kvEntry.getValue());
                }
            }
            return mapEx;
        }

        public List<K> filterByValue(V value) {
            List<K> keys = new ArrayList<>();
            for (Entry<K, V> kvEntry : this.entrySet()) {
                if (kvEntry.getValue().equals(value)) {
                    keys.add(kvEntry.getKey());
                }
            }
            return keys;
        }
    }
}
