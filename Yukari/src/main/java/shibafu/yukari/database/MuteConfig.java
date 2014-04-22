package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Created by shibafu on 14/04/22.
 */
public class MuteConfig implements DBRecord{
    public static final int SCOPE_TEXT = 0;
    public static final int SCOPE_USER_NAME = 1;
    public static final int SCOPE_USER_SN = 2;
    public static final int SCOPE_USER_ID = 3;
    public static final int SCOPE_VIA = 4;

    public static final int MATCH_EXACT = 0;
    public static final int MATCH_PARTIAL = 1;
    public static final int MATCH_REGEX = 2;

    public static final int MUTE_TWEET = 0x01;
    public static final int MUTE_TWEET_RTED = 0x02;
    public static final int MUTE_NOTIF_FAV = 0x04;
    public static final int MUTE_NOTIF_RT = 0x08;
    public static final int MUTE_NOTIF_MENTION = 0x10;
    public static final int MUTE_IMAGE_THUMB = 0x20;
    public static final int MUTE_IMAGE_ICON = 0x40;

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
}
