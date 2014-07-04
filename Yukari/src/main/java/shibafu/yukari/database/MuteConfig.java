package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by shibafu on 14/04/22.
 */
public class MuteConfig implements DBRecord, Serializable, Parcelable {
    public static final int SCOPE_TEXT = 0;
    public static final int SCOPE_USER_NAME = 1;
    public static final int SCOPE_USER_SN = 2;
    public static final int SCOPE_USER_ID = 3;
    public static final int SCOPE_VIA = 4;

    public static final int MATCH_EXACT = 0;
    public static final int MATCH_PARTIAL = 1;
    public static final int MATCH_REGEX = 2;

    public static final int MUTE_TWEET = 0;
    public static final int MUTE_TWEET_RTED = 1;
    public static final int MUTE_RETWEET = 2;
    public static final int MUTE_NOTIF_FAV = 3;
    public static final int MUTE_NOTIF_RT = 4;
    public static final int MUTE_NOTIF_MENTION = 5;
    public static final int MUTE_IMAGE_THUMB = 6;

    private long id = -1;
    private int scope; //検査対象
    private int match; //マッチング方法
    private int mute; //ミュート対象
    private String query; //検査クエリ

    public MuteConfig(int scope, int match, int mute, String query) {
        this.scope = scope;
        this.match = match;
        this.mute = mute;
        this.query = query;
    }

    public MuteConfig(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_MUTE_ID));
        scope = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_SCOPE));
        match = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_MATCH));
        mute = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_MUTE));
        query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_MUTE_QUERY));
    }

    public long getId() {
        return id;
    }

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        this.scope = scope;
    }

    public int getMatch() {
        return match;
    }

    public void setMatch(int match) {
        this.match = match;
    }

    public int getMute() {
        return mute;
    }

    public void setMute(int mute) {
        this.mute = mute;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (id > -1) {
            values.put(CentralDatabase.COL_MUTE_ID, id);
        }
        values.put(CentralDatabase.COL_MUTE_SCOPE, scope);
        values.put(CentralDatabase.COL_MUTE_MATCH, match);
        values.put(CentralDatabase.COL_MUTE_MUTE, mute);
        values.put(CentralDatabase.COL_MUTE_QUERY, query);
        return values;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeInt(this.scope);
        dest.writeInt(this.match);
        dest.writeInt(this.mute);
        dest.writeString(this.query);
    }

    private MuteConfig(Parcel in) {
        this.id = in.readLong();
        this.scope = in.readInt();
        this.match = in.readInt();
        this.mute = in.readInt();
        this.query = in.readString();
    }

    public static final Parcelable.Creator<MuteConfig> CREATOR = new Parcelable.Creator<MuteConfig>() {
        public MuteConfig createFromParcel(Parcel source) {
            return new MuteConfig(source);
        }

        public MuteConfig[] newArray(int size) {
            return new MuteConfig[size];
        }
    };
}
