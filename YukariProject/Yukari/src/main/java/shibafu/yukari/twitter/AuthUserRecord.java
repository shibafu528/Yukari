package shibafu.yukari.twitter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.v7.appcompat.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.common.ProfileIconCache;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;

public class AuthUserRecord implements Serializable, DBRecord{
	private static final long serialVersionUID = 1L;

	public long NumericId;
	public String ScreenName;
    public String Name;
    public String ProfileImageUrl;
    public boolean isPrimary;
    public boolean isActive;
    public boolean isWriter;
	public AccessToken Token;

    public AuthUserRecord(AccessToken token) {
        Token = token;
        NumericId = token.getUserId();
        ScreenName = token.getScreenName();
        isActive = true;
    }

    public AuthUserRecord(Cursor cursor) {
        NumericId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ID));
        ScreenName = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_SCREEN_NAME));
        Name = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_NAME));
        ProfileImageUrl = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_USER_PROFILE_IMAGE_URL));
        isPrimary = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_PRIMARY)) == 1;
        isActive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_ACTIVE)) == 1;
        isWriter = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_WRITER)) == 1;
        String accessToken = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN));
        String accessTokenSecret = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN_SECRET));
        Token = new AccessToken(accessToken, accessTokenSecret, NumericId);
    }

    public AccessToken getAccessToken() {
		return Token;
	}

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null)
            return false;
        if (o.getClass() != this.getClass())
            return false;
        if (((AuthUserRecord)o).NumericId == NumericId)
            return true;
        else
            return false;
    }

    @Override
    public int hashCode() {
        return Integer.decode(ScreenName);
    }

    public List<AuthUserRecord> toSingleList() {
        List<AuthUserRecord> l = new ArrayList<AuthUserRecord>();
        l.add(this);
        return l;
    }

    public User getUser(Context context) throws TwitterException {
        Twitter twitter = TwitterUtil.getTwitterInstance(context);
        twitter.setOAuthAccessToken(getAccessToken());
        return twitter.showUser(getAccessToken().getUserId());
    }

    public static List<AuthUserRecord> getAccountsList(Cursor cursor) {
        List<AuthUserRecord> records = new ArrayList<AuthUserRecord>();
        if (cursor.moveToFirst()) {
            do {
                records.add(new AuthUserRecord(cursor));
            } while (cursor.moveToNext());
        }
        return records;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(CentralDatabase.COL_ACCOUNTS_ID, NumericId);
        values.put(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN, Token.getToken());
        values.put(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN_SECRET, Token.getTokenSecret());
        values.put(CentralDatabase.COL_ACCOUNTS_IS_PRIMARY, isPrimary);
        values.put(CentralDatabase.COL_ACCOUNTS_IS_ACTIVE, isActive);
        values.put(CentralDatabase.COL_ACCOUNTS_IS_WRITER, isWriter);
        return values;
    }

    public void update(AuthUserRecord aur) {
        NumericId = aur.NumericId;
        ScreenName = aur.ScreenName;
        Name = aur.Name;
        ProfileImageUrl = aur.ProfileImageUrl;
        isPrimary = aur.isPrimary;
        isActive = aur.isActive;
        isWriter = aur.isWriter;
        Token = aur.Token;
    }
}
