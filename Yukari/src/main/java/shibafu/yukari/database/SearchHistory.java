package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.Date;

import lombok.Value;

/**
 * Created by shibafu on 14/03/10.
 */
@Value
@DBTable(CentralDatabase.TABLE_SEARCH_HISTORY)
public class SearchHistory implements DBRecord{
    private final long id;
    private final String query;
    private final Date date;

    public SearchHistory(String query, Date date) {
        this.id = -1;
        this.query = query;
        this.date = date;
    }

    public SearchHistory(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_ID));
        this.query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_QUERY));
        this.date = new Date(cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_DATE)));
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (id > -1) {
            values.put(CentralDatabase.COL_SHISTORY_ID, id);
        }
        values.put(CentralDatabase.COL_SHISTORY_QUERY, query);
        values.put(CentralDatabase.COL_SHISTORY_DATE, date.getTime());
        return values;
    }

    @Override
    public String toString() {
        return query;
    }
}
