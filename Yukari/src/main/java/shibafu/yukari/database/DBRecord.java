package shibafu.yukari.database;

import android.content.ContentValues;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * Created by Shibafu on 13/12/19.
 */
public interface DBRecord {
    default ContentValues getContentValues() {
        ContentValues values = new ContentValues();

        for (Field f : this.getClass().getDeclaredFields()) {
            DBColumn column = f.getAnnotation(DBColumn.class);
            if (column != null) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(this);
                    if (val instanceof String || val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Float || val instanceof Double) {
                        if ("id".equals(f.getName()) && val instanceof Long && ((Long) val) == -1) {
                            continue;
                        }
                        values.put(column.value(), val.toString());
                    } else if (val instanceof Date) {
                        values.put(column.value(), ((Date) val).getTime());
                    } else {
                        throw new IllegalStateException("field cannot put ContentValues: " + f.getName());
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return values;
    }
}
