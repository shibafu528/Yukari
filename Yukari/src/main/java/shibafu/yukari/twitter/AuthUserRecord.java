package shibafu.yukari.twitter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import shibafu.yukari.R;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBRecord;
import shibafu.yukari.database.DBTable;
import shibafu.yukari.database.Provider;
import twitter4j.Twitter;
import twitter4j.auth.AccessToken;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@DBTable(CentralDatabase.TABLE_ACCOUNTS)
public class AuthUserRecord implements Serializable, DBRecord {
    private static final long serialVersionUID = 1L;

    public long InternalId = -1;
    public long NumericId;
    public String ScreenName;
    public String Name;
    public String ProfileImageUrl;
    public boolean isPrimary;
    public boolean isActive;
    public boolean isWriter;
    public String AccessToken;
    public String AccessTokenSecret;
    public int AccountColor;
    public Provider Provider;

    private AccessToken twitterAccessToken;

    private static LongSparseArray<HashMap<String, Object>> sessionTemporary = new LongSparseArray<>();

    public AuthUserRecord(AccessToken token) {
        twitterAccessToken = token;
        NumericId = token.getUserId();
        ScreenName = token.getScreenName();
        isActive = true;
        AccountColor = Color.TRANSPARENT;
        AccessToken = token.getToken();
        AccessTokenSecret = token.getTokenSecret();
        Provider = shibafu.yukari.database.Provider.TWITTER;
    }

    public AuthUserRecord(Cursor cursor) {
        InternalId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ID));
        NumericId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_USER_ID));
        ScreenName = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_SCREEN_NAME));
        Name = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_DISPLAY_NAME));
        ProfileImageUrl = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_PROFILE_IMAGE_URL));
        isPrimary = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_PRIMARY)) == 1;
        isActive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_ACTIVE)) == 1;
        isWriter = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_IS_WRITER)) == 1;
        AccessToken = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN));
        AccessTokenSecret = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN_SECRET));
        AccountColor = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_COLOR));
        if (cursor.isNull(cursor.getColumnIndex(CentralDatabase.COL_ACCOUNTS_PROVIDER_ID))) {
            Provider = shibafu.yukari.database.Provider.TWITTER;
        } else {
            Provider = new Provider(cursor);
        }
    }

    public AccessToken getTwitterAccessToken() {
        if (twitterAccessToken == null) {
            twitterAccessToken = new AccessToken(AccessToken, AccessTokenSecret, NumericId);
        }
        return twitterAccessToken;
    }

    /**
     * デフォルトのコンシューマキーを使用する必要があるかどうかを取得します。
     * @return デフォルトコンシューマキーの必要性
     */
    public boolean isDefaultConsumer() {
        return TextUtils.isEmpty(Provider.getConsumerKey()) || TextUtils.isEmpty(Provider.getConsumerSecret());
    }

    /**
     * Twitterインスタンスの認証情報をこのアカウントを利用するための認証情報で上書きします。
     * @param context Context
     * @param twitter 上書きするTwitterインスタンス
     */
    public void updateTwitterInstance(Context context, Twitter twitter) {
        if (isDefaultConsumer()) {
            twitter.setOAuthConsumer(context.getString(R.string.twitter_consumer_key),
                    context.getString(R.string.twitter_consumer_secret));
        } else {
            twitter.setOAuthConsumer(Provider.getConsumerKey(), Provider.getConsumerSecret());
        }
        twitter.setOAuthAccessToken(getTwitterAccessToken());
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
        return (int)(InternalId ^ (InternalId >>> 32));
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

    /**
     * 拡張機能を実行することが可能か確認します。<br>
     * 1アカウントでもCK/CSオーバライドを行っていれば可能と判断されます。
     * @param userRecords アカウント
     * @return 拡張機能の実行許可
     */
    public static boolean canUseDissonanceFunctions(List<AuthUserRecord> userRecords) {
        for (AuthUserRecord userRecord : userRecords) {
            if (!userRecord.isDefaultConsumer()) return true;
        }
        return false;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (InternalId != -1) {
            values.put(CentralDatabase.COL_ACCOUNTS_ID, InternalId);
        }
        values.put(CentralDatabase.COL_ACCOUNTS_USER_ID, NumericId);
        values.put(CentralDatabase.COL_ACCOUNTS_SCREEN_NAME, ScreenName);
        values.put(CentralDatabase.COL_ACCOUNTS_DISPLAY_NAME, Name);
        values.put(CentralDatabase.COL_ACCOUNTS_PROFILE_IMAGE_URL, ProfileImageUrl);
        values.put(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN, AccessToken);
        values.put(CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN_SECRET, AccessTokenSecret);
        values.put(CentralDatabase.COL_ACCOUNTS_IS_PRIMARY, isPrimary);
        values.put(CentralDatabase.COL_ACCOUNTS_IS_ACTIVE, isActive);
        values.put(CentralDatabase.COL_ACCOUNTS_IS_WRITER, isWriter);
        values.put(CentralDatabase.COL_ACCOUNTS_COLOR, AccountColor);
        if (Provider != shibafu.yukari.database.Provider.TWITTER) {
            values.put(CentralDatabase.COL_ACCOUNTS_PROVIDER_ID, Provider.getId());
        }
        return values;
    }

    public void update(AuthUserRecord aur) {
        InternalId = aur.InternalId;
        NumericId = aur.NumericId;
        ScreenName = aur.ScreenName;
        Name = aur.Name;
        ProfileImageUrl = aur.ProfileImageUrl;
        isPrimary = aur.isPrimary;
        isActive = aur.isActive;
        isWriter = aur.isWriter;
        AccessToken = aur.AccessToken;
        AccessTokenSecret = aur.AccessTokenSecret;
        Provider = aur.Provider;

        twitterAccessToken = aur.twitterAccessToken;
    }

    public Object getSessionTemporary(String key) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<>());
        }
        return sessionTemporary.get(NumericId).get(key);
    }

    public Object getSessionTemporary(String key, Object ifNull) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<>());
        }
        Object value = sessionTemporary.get(NumericId).get(key);
        return value != null ? value : ifNull;
    }

    public void putSessionTemporary(String key, Object value) {
        if (sessionTemporary.indexOfKey(NumericId) < 0) {
            sessionTemporary.put(NumericId, new HashMap<>());
        }
        sessionTemporary.get(NumericId).put(key, value);
    }

    @Override
    public String toString() {
        return "AuthUserRecord{" +
                "InternalId=" + InternalId +
                ", NumericId=" + NumericId +
                ", ScreenName='" + ScreenName + '\'' +
                ", Name='" + Name + '\'' +
                ", ProfileImageUrl='" + ProfileImageUrl + '\'' +
                ", isPrimary=" + isPrimary +
                ", isActive=" + isActive +
                ", isWriter=" + isWriter +
                ", AccessToken=" + AccessToken +
                ", AccessTokenSecret=" + (TextUtils.isEmpty(AccessTokenSecret) ? "" : "****") +
                ", AccountColor=" + AccountColor +
                ", Provider=" + Provider +
                '}';
    }
}
