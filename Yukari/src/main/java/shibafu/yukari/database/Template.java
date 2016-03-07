package shibafu.yukari.database;

import android.database.Cursor;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import static shibafu.yukari.database.CentralDatabase.*;

/**
 * Created by shibafu on 15/06/23.
 */
@Data
@DBTable(TABLE_TEMPLATE)
public class Template implements DBRecord, Serializable{
    @Setter(AccessLevel.NONE)
    @DBColumn(COL_TEMPLATE_ID) private long id = -1;
    @DBColumn(COL_TEMPLATE_VALUE) private String value;

    public Template(String value) {
        this.value = value;
    }

    public Template(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(COL_TEMPLATE_ID));
        this.value = cursor.getString(cursor.getColumnIndex(COL_TEMPLATE_VALUE));
    }

    @Override
    public String toString() {
        return value;
    }
}
