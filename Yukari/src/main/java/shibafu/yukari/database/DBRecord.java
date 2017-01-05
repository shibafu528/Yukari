package shibafu.yukari.database;

import android.content.ContentValues;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * Created by Shibafu on 13/12/19.
 */
public interface DBRecord {
    ContentValues getContentValues();

    class DBRecordUtil {
        public static ContentValues getContentValues(DBRecord target) {
            ContentValues values = new ContentValues();

            for (Field f : target.getClass().getDeclaredFields()) {
                DBColumn column = f.getAnnotation(DBColumn.class);
                if (column != null) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(target);
                        if (value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Short ||
                                value instanceof Float || value instanceof Double) {
                            if ("id".equals(f.getName()) && value instanceof Long && ((Long) value) == -1) {
                                continue;
                            }
                            values.put(column.value(), value.toString());
                        } else if (value instanceof Date) {
                            values.put(column.value(), ((Date) value).getTime());
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
}
