package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.Date;

/**
 * Created by shibafu on 14/03/10.
 */
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHistory)) return false;

        SearchHistory that = (SearchHistory) o;

        if (id != that.id) return false;
        if (query != null ? !query.equals(that.query) : that.query != null) return false;
        return date != null ? date.equals(that.date) : that.date == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (query != null ? query.hashCode() : 0);
        result = 31 * result + (date != null ? date.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return query;
    }
}
