package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * Created by shibafu on 15/06/23.
 */
@Data
@DBTable(CentralDatabase.TABLE_TEMPLATE)
public class Template implements DBRecord, Serializable{
    @Setter(AccessLevel.NONE) private long id = -1;
    private String value;

    public Template(String value) {
        this.value = value;
    }

    public Template(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_TEMPLATE_ID));
        this.value = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_TEMPLATE_VALUE));
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (id > -1) {
            values.put(CentralDatabase.COL_TEMPLATE_ID, id);
        }
        values.put(CentralDatabase.COL_TEMPLATE_VALUE, value);
        return values;
    }
}
