package shibafu.dissonance.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.Date;

/**
 * Created by shibafu on 14/03/10.
 */
@DBTable(CentralDatabase.TABLE_SEARCH_HISTORY)
public class SearchHistory implements DBRecord{
    private long id = -1;
    private String query;
    private Date date;

    public SearchHistory(String query, Date date) {
        this.query = query;
        this.date = date;
    }

    public SearchHistory(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_ID));
        this.query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_QUERY));
        this.date = new Date(cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_SHISTORY_DATE)));
    }

    public long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public Date getDate() {
        return date;
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
