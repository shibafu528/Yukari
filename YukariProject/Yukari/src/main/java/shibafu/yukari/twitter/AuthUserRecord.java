package shibafu.yukari.twitter;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.support.v4.util.LongSparseArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBRecord;
import shibafu.yukari.database.DBTable;
import twitter4j.auth.AccessToken;

@DBTable(CentralDatabase.TABLE_ACCOUNTS)
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
    public int AccountColor;

    private static LongSparseArray<HashMap<String, Object>> sessionTemporary = new LongSparseArray<>();

    public AuthUserRecord(AccessToken token) {
        Token = token;
        NumericId = token.getUserId();
        ScreenName = token.getScreenName();
        isActive = true;
        AccountColor = Color.TRANSPARENT;
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
        AccountColor = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_COLOR));
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
        return ScreenName.hashCode();
    }

    public ArrayList<AuthUserRecord> toSingleList() {
        ArrayList<AuthUserRecord> l = new ArrayList<>();
        l.add(this);
        return l;
    }

    public static List<AuthUserRecord> getAccountsList(Cursor cursor) {
        List<AuthUserRecord> records = new ArrayList<>();
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
        values.put(CentralDatabase.COL_ACCOUNTS_COLOR, AccountColor);
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

    public Object getSessionTemporary(String key) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<String, Object>());
        }
        return sessionTemporary.get(NumericId).get(key);
    }

    public Object getSessionTemporary(String key, Object ifNull) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<String, Object>());
        }
        Object value = sessionTemporary.get(NumericId).get(key);
        return value != null ? value : ifNull;
    }

    public void putSessionTemporary(String key, Object value) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<String, Object>());
        }
        sessionTemporary.get(NumericId).put(key, value);
    }
}
