package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Created by shibafu on 14/12/14.
 */
@DBTable(CentralDatabase.TABLE_AUTO_MUTE)
public class AutoMuteConfig implements DBRecord, MuteMatch {
    private long id = -1;
    private int match; //マッチング方法
    private String query; //検査クエリ
    private String targetScreenName; //ターゲットSN

    public AutoMuteConfig(int match, String query, String targetScreenName) {
        this.match = match;
        this.query = query;
        this.targetScreenName = targetScreenName;
    }

    public AutoMuteConfig(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_ID));
        match = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_MATCH));
        query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_QUERY));
        targetScreenName = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_AUTO_MUTE_TARGET_SN));
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

    public String getTargetScreenName() {
        return targetScreenName;
    }

    public void setTargetScreenName(String targetScreenName) {
        this.targetScreenName = targetScreenName;
    }

    public MuteConfig getMuteConfig(long expirationTimeMillis) {
        return new MuteConfig(MuteConfig.SCOPE_USER_SN,
                MuteConfig.MATCH_EXACT,
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
        values.put(CentralDatabase.COL_AUTO_MUTE_TARGET_SN, targetScreenName);
        return values;
    }
}
