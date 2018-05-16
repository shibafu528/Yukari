package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.io.Serializable;

/**
 * Created by shibafu on 14/12/14.
 */
@DBTable(CentralDatabase.TABLE_AUTO_MUTE)
public class AutoMuteConfig implements DBRecord, Serializable, MuteMatch {
    private long id = -1;
    private int match; //マッチング方法
    private String query; //検査クエリ

    public AutoMuteConfig(int match, String query) {
        this.match = match;
        this.query = query;
    }

    public AutoMuteConfig(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_ID));
        match = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_MATCH));
        query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_QUERY));
    }

    public long getId() {
        return id;
    }

    public int getMatch() {
        return match;
    }

    public void setMatch(int match) {
        this.match = match;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public MuteConfig getMuteConfig(String targetScreenName, long expirationTimeMillis) {
        return new MuteConfig(MuteConfig.SCOPE_USER_SN,
                MuteMatch.MATCH_EXACT,
                MuteConfig.MUTE_TWEET,
                targetScreenName,
                expirationTimeMillis);
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (id > -1) {
            values.put(CentralDatabase.COL_AUTO_MUTE_ID, id);
        }
        values.put(CentralDatabase.COL_AUTO_MUTE_MATCH, match);
        values.put(CentralDatabase.COL_AUTO_MUTE_QUERY, query);
        return values;
    }
}
