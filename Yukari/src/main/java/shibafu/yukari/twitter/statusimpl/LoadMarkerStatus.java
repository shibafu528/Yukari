package shibafu.yukari.twitter.statusimpl;

import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;

import java.util.Date;

/**
* Created by shibafu on 14/06/19.
*/
public class LoadMarkerStatus extends FakeStatus {
    private long anchorTweetId;
    private long userId;

    public LoadMarkerStatus(long id, long anchorTweetId, long userId) {
        super(id);
        this.anchorTweetId = anchorTweetId;
        this.userId = userId;
    }

    public long getAnchorTweetId() {
        return anchorTweetId;
    }

    @Override
    public User getUser() {
        return new User() {
            @Override
            public long getId() {
                return userId;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            public String getScreenName() {
                return "";
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
                return LoadMarkerStatus.this;
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
        };
    }
}
