package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;

/**
 * Created by Shibafu on 13/12/20.
 */
@DBTable(CentralDatabase.TABLE_USER)
public class DBUser implements User, DBRecord {
    private long id;
    private String screenName;
    private String name;
    private String description;
    private URLEntity[] descriptionURLEntities;
    private String location;
    private String url;
    private String profileImageUrl;
    private String profileBannerUrl;
    private boolean isProtected;
    private boolean isVerified;
    private boolean isTranslator;
    private boolean isContributorsEnabled;
    private boolean isGeoEnabled;
    private int statusesCount;
    private int followingsCount;
    private int followersCount;
    private int favoritesCount;
    private int listedCount;
    private String language;
    private long createdAt;

    public DBUser(User user) {
        id = user.getId();
        screenName = user.getScreenName();
        name = user.getName();
        description = user.getDescription();
        descriptionURLEntities = user.getDescriptionURLEntities();
        location = user.getLocation();
        url = user.getURLEntity().getExpandedURL();
        profileImageUrl = user.getProfileImageURLHttps();
        profileBannerUrl = user.getProfileBannerURL();
        isProtected = user.isProtected();
        isVerified = user.isVerified();
        isTranslator = user.isTranslator();
        isContributorsEnabled = user.isContributorsEnabled();
        isGeoEnabled = user.isGeoEnabled();
        statusesCount = user.getStatusesCount();
        followingsCount = user.getFriendsCount();
        followersCount = user.getFollowersCount();
        favoritesCount = user.getFavouritesCount();
        listedCount = user.getListedCount();
        language = user.getLang();
        createdAt = user.getCreatedAt().getTime();
    }

    public DBUser(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_USER_ID));
        screenName = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_SCREEN_NAME));
        name = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_NAME));
        description = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_DESCRIPTION));
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(cursor.getBlob(cursor.getColumnIndex(CentralDatabase.COL_USER_DESCRIPTION_URLENTITIES))));
            descriptionURLEntities = (URLEntity[]) ois.readObject();
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }
        location = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_LOCATION));
        url = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_URL));
        profileImageUrl = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_PROFILE_IMAGE_URL));
        profileBannerUrl = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_PROFILE_BANNER_URL));
        isProtected = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_IS_PROTECTED)) == 1;
        isVerified = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_IS_VERIFIED)) == 1;
        isTranslator = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_IS_TRANSLATOR)) == 1;
        isContributorsEnabled = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_IS_CONTRIBUTORS_ENABLED)) == 1;
        isGeoEnabled = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_IS_GEO_ENABLED)) == 1;
        statusesCount = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_STATUSES_COUNT));
        followingsCount = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_FOLLOWINGS_COUNT));
        followersCount = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_FOLLOWERS_COUNT));
        favoritesCount = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_FAVORITES_COUNT));
        listedCount = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_USER_LISTED_COUNT));
        language = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_LANGUAGE));
        createdAt = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_USER_CREATED_AT));
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(CentralDatabase.COL_USER_ID, id);
        values.put(CentralDatabase.COL_USER_SCREEN_NAME, screenName);
        values.put(CentralDatabase.COL_USER_NAME, name);
        values.put(CentralDatabase.COL_USER_DESCRIPTION, description);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(descriptionURLEntities);
            values.put(CentralDatabase.COL_USER_DESCRIPTION_URLENTITIES, baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        values.put(CentralDatabase.COL_USER_LOCATION, location);
        values.put(CentralDatabase.COL_USER_URL, url);
        values.put(CentralDatabase.COL_USER_PROFILE_IMAGE_URL, profileImageUrl);
        values.put(CentralDatabase.COL_USER_PROFILE_BANNER_URL, profileBannerUrl);
        values.put(CentralDatabase.COL_USER_IS_PROTECTED, isProtected);
        values.put(CentralDatabase.COL_USER_IS_VERIFIED, isVerified);
        values.put(CentralDatabase.COL_USER_IS_TRANSLATOR, isTranslator);
        values.put(CentralDatabase.COL_USER_IS_CONTRIBUTORS_ENABLED, isContributorsEnabled);
        values.put(CentralDatabase.COL_USER_IS_GEO_ENABLED, isGeoEnabled);
        values.put(CentralDatabase.COL_USER_STATUSES_COUNT, statusesCount);
        values.put(CentralDatabase.COL_USER_FOLLOWINGS_COUNT, followingsCount);
        values.put(CentralDatabase.COL_USER_FOLLOWERS_COUNT, followersCount);
        values.put(CentralDatabase.COL_USER_FAVORITES_COUNT, favoritesCount);
        values.put(CentralDatabase.COL_USER_LISTED_COUNT, listedCount);
        values.put(CentralDatabase.COL_USER_LANGUAGE, language);
        values.put(CentralDatabase.COL_USER_CREATED_AT, createdAt);
        return values;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getScreenName() {
        return screenName;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isContributorsEnabled() {
        return isContributorsEnabled;
    }

    @Override
    public String getProfileImageURL() {
        return profileImageUrl;
    }

    @Override
    public String getBiggerProfileImageURL() {
        return profileImageUrl;
    }

    @Override
    public String getMiniProfileImageURL() {
        return profileImageUrl;
    }

    @Override
    public String getOriginalProfileImageURL() {
        return profileImageUrl;
    }

    @Override
    public String getProfileImageURLHttps() {
        return profileImageUrl;
    }

    @Override
    public String getBiggerProfileImageURLHttps() {
        return profileImageUrl;
    }

    @Override
    public String getMiniProfileImageURLHttps() {
        return profileImageUrl;
    }

    @Override
    public String getOriginalProfileImageURLHttps() {
        return profileImageUrl;
    }

    @Override
    public boolean isDefaultProfileImage() {
        return false;
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public boolean isProtected() {
        return isProtected;
    }

    @Override
    public int getFollowersCount() {
        return followersCount;
    }

    @Override
    @Deprecated
    public Status getStatus() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileBackgroundColor() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileTextColor() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileLinkColor() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileSidebarFillColor() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileSidebarBorderColor() {
        return null;
    }

    @Override
    @Deprecated
    public boolean isProfileUseBackgroundImage() {
        return false;
    }

    @Override
    public boolean isDefaultProfile() {
        return false;
    }

    @Override
    @Deprecated
    public boolean isShowAllInlineMedia() {
        return false;
    }

    @Override
    public int getFriendsCount() {
        return followingsCount;
    }

    @Override
    public Date getCreatedAt() {
        return new Date(createdAt);
    }

    @Override
    public int getFavouritesCount() {
        return favoritesCount;
    }

    @Override
    @Deprecated
    public int getUtcOffset() {
        return 0;
    }

    @Override
    @Deprecated
    public String getTimeZone() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileBackgroundImageURL() {
        return null;
    }

    @Override
    @Deprecated
    public String getProfileBackgroundImageUrlHttps() {
        return null;
    }

    @Override
    public String getProfileBannerURL() {
        return profileBannerUrl;
    }

    @Override
    public String getProfileBannerRetinaURL() {
        return profileBannerUrl;
    }

    @Override
    public String getProfileBannerIPadURL() {
        return profileBannerUrl;
    }

    @Override
    public String getProfileBannerIPadRetinaURL() {
        return profileBannerUrl;
    }

    @Override
    public String getProfileBannerMobileURL() {
        return profileBannerUrl;
    }

    @Override
    public String getProfileBannerMobileRetinaURL() {
        return profileBannerUrl;
    }

    @Override
    @Deprecated
    public boolean isProfileBackgroundTiled() {
        return false;
    }

    @Override
    public String getLang() {
        return language;
    }

    @Override
    public int getStatusesCount() {
        return statusesCount;
    }

    @Override
    public boolean isGeoEnabled() {
        return isGeoEnabled;
    }

    @Override
    public boolean isVerified() {
        return isVerified;
    }

    @Override
    public boolean isTranslator() {
        return isTranslator;
    }

    @Override
    public int getListedCount() {
        return listedCount;
    }

    @Override
    @Deprecated
    public boolean isFollowRequestSent() {
        return false;
    }

    @Override
    public URLEntity[] getDescriptionURLEntities() {
        return descriptionURLEntities;
    }

    @Override
    @Deprecated
    public URLEntity getURLEntity() {
        return new URLEntity() {
            @Override
            public String getText() {
                return url;
            }

            @Override
            public String getURL() {
                return url;
            }

            @Override
            public String getExpandedURL() {
                return url;
            }

            @Override
            public String getDisplayURL() {
                return url;
            }

            @Override
            public int getStart() {
                return 0;
            }

            @Override
            public int getEnd() {
                return url.length();
            }
        };
    }

    @Override
    public String[] getWithheldInCountries() {
        return new String[0];
    }

    @Override
    public int compareTo(User another) {
        long dist = id - another.getId();
        if (dist > 0) return 1;
        else if (dist < 0) return -1;
        else return 0;
    }

    @Override
    @Deprecated
    public RateLimitStatus getRateLimitStatus() {
        return null;
    }

    @Override
    @Deprecated
    public int getAccessLevel() {
        return 0;
    }
}
