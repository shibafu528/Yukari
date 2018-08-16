package shibafu.yukari.twitter.statusimpl;

import twitter4j.*;

import java.util.Date;

/**
* Created by shibafu on 14/06/19.
*/
public class FakeStatus implements Status {
    private static final User PSEUDO_USER = new FakeUser();

    private long id;
    private Date createdAt = new Date();

    public FakeStatus(long id) {
        this.id = id;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getText() {
        return "";
    }

    @Override
    public int getDisplayTextRangeStart() {
        return 0;
    }

    @Override
    public int getDisplayTextRangeEnd() {
        return getText().length() - 1;
    }

    @Override
    public String getSource() {
        return "";
    }

    @Override
    public boolean isTruncated() {
        return false;
    }

    @Override
    public long getInReplyToStatusId() {
        return 0;
    }

    @Override
    public long getInReplyToUserId() {
        return 0;
    }

    @Override
    public String getInReplyToScreenName() {
        return "";
    }

    @Override
    public GeoLocation getGeoLocation() {
        return null;
    }

    @Override
    public Place getPlace() {
        return null;
    }

    @Override
    public boolean isFavorited() {
        return false;
    }

    @Override
    public boolean isRetweeted() {
        return false;
    }

    @Override
    public int getFavoriteCount() {
        return 0;
    }

    @Override
    public User getUser() {
        return PSEUDO_USER;
    }

    @Override
    public boolean isRetweet() {
        return false;
    }

    @Override
    public Status getRetweetedStatus() {
        return null;
    }

    @Override
    public long[] getContributors() {
        return new long[0];
    }

    @Override
    public int getRetweetCount() {
        return 0;
    }

    @Override
    public boolean isRetweetedByMe() {
        return false;
    }

    @Override
    public long getCurrentUserRetweetId() {
        return 0;
    }

    @Override
    public boolean isPossiblySensitive() {
        return false;
    }

    @Override
    public String getLang() {
        return "";
    }

    @Override
    public Scopes getScopes() {
        return null;
    }

    @Override
    public String[] getWithheldInCountries() {
        return new String[0];
    }

    @Override
    public long getQuotedStatusId() {
        return 0;
    }

    @Override
    public Status getQuotedStatus() {
        return null;
    }

    @Override
    public URLEntity getQuotedStatusPermalink() {
        return null;
    }

    @Override
    public int compareTo(Status another) {
        return 0;
    }

    @Override
    public UserMentionEntity[] getUserMentionEntities() {
        return new UserMentionEntity[0];
    }

    @Override
    public URLEntity[] getURLEntities() {
        return new URLEntity[0];
    }

    @Override
    public HashtagEntity[] getHashtagEntities() {
        return new HashtagEntity[0];
    }

    @Override
    public MediaEntity[] getMediaEntities() {
        return new MediaEntity[0];
    }

    @Override
    public SymbolEntity[] getSymbolEntities() {
        return new SymbolEntity[0];
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return null;
    }

    @Override
    public int getAccessLevel() {
        return 0;
    }

    protected static class FakeUser implements User {
        @Override
        public long getId() {
            return 0;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getEmail() {
            return "";
        }

        @Override
        public String getScreenName() {
            return "**FakeStatus**";
        }

        @Override
        public String getLocation() {
            return "";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public boolean isContributorsEnabled() {
            return false;
        }

        @Override
        public String getProfileImageURL() {
            return "";
        }

        @Override
        public String getBiggerProfileImageURL() {
            return "";
        }

        @Override
        public String getMiniProfileImageURL() {
            return "";
        }

        @Override
        public String getOriginalProfileImageURL() {
            return "";
        }

        @Override
        public String get400x400ProfileImageURL() {
            return "";
        }

        @Override
        public String getProfileImageURLHttps() {
            return "";
        }

        @Override
        public String getBiggerProfileImageURLHttps() {
            return "";
        }

        @Override
        public String getMiniProfileImageURLHttps() {
            return "";
        }

        @Override
        public String getOriginalProfileImageURLHttps() {
            return "";
        }

        @Override
        public String get400x400ProfileImageURLHttps() {
            return "";
        }

        @Override
        public boolean isDefaultProfileImage() {
            return false;
        }

        @Override
        public String getURL() {
            return "";
        }

        @Override
        public boolean isProtected() {
            return false;
        }

        @Override
        public int getFollowersCount() {
            return 0;
        }

        @Override
        public Status getStatus() {
            return new FakeStatus(0);
        }

        @Override
        public String getProfileBackgroundColor() {
            return "";
        }

        @Override
        public String getProfileTextColor() {
            return "";
        }

        @Override
        public String getProfileLinkColor() {
            return "";
        }

        @Override
        public String getProfileSidebarFillColor() {
            return "";
        }

        @Override
        public String getProfileSidebarBorderColor() {
            return "";
        }

        @Override
        public boolean isProfileUseBackgroundImage() {
            return false;
        }

        @Override
        public boolean isDefaultProfile() {
            return false;
        }

        @Override
        public boolean isShowAllInlineMedia() {
            return false;
        }

        @Override
        public int getFriendsCount() {
            return 0;
        }

        @Override
        public Date getCreatedAt() {
            return new Date(System.currentTimeMillis());
        }

        @Override
        public int getFavouritesCount() {
            return 0;
        }

        @Override
        public int getUtcOffset() {
            return 0;
        }

        @Override
        public String getTimeZone() {
            return "";
        }

        @Override
        public String getProfileBackgroundImageURL() {
            return "";
        }

        @Override
        public String getProfileBackgroundImageUrlHttps() {
            return "";
        }

        @Override
        public String getProfileBannerURL() {
            return "";
        }

        @Override
        public String getProfileBannerRetinaURL() {
            return "";
        }

        @Override
        public String getProfileBannerIPadURL() {
            return "";
        }

        @Override
        public String getProfileBannerIPadRetinaURL() {
            return "";
        }

        @Override
        public String getProfileBannerMobileURL() {
            return "";
        }

        @Override
        public String getProfileBannerMobileRetinaURL() {
            return "";
        }

        @Override
        public String getProfileBanner300x100URL() {
            return "";
        }

        @Override
        public String getProfileBanner600x200URL() {
            return "";
        }

        @Override
        public String getProfileBanner1500x500URL() {
            return "";
        }

        @Override
        public boolean isProfileBackgroundTiled() {
            return false;
        }

        @Override
        public String getLang() {
            return "";
        }

        @Override
        public int getStatusesCount() {
            return 0;
        }

        @Override
        public boolean isGeoEnabled() {
            return false;
        }

        @Override
        public boolean isVerified() {
            return false;
        }

        @Override
        public boolean isTranslator() {
            return false;
        }

        @Override
        public int getListedCount() {
            return 0;
        }

        @Override
        public boolean isFollowRequestSent() {
            return false;
        }

        @Override
        public URLEntity[] getDescriptionURLEntities() {
            return new URLEntity[0];
        }

        @Override
        public URLEntity getURLEntity() {
            return null;
        }

        @Override
        public String[] getWithheldInCountries() {
            return new String[0];
        }

        @Override
        public int compareTo(User another) {
            return 0;
        }

        @Override
        public RateLimitStatus getRateLimitStatus() {
            return null;
        }

        @Override
        public int getAccessLevel() {
            return 0;
        }
    }
}
