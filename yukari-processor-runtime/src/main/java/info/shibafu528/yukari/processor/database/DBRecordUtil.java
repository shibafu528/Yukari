package info.shibafu528.yukari.processor.database;

import android.content.ContentValues;

import java.lang.reflect.Method;

public class DBRecordUtil {
    public static ContentValues getContentValues(Object target) {
        String autoReleaseClassName = target.getClass().getCanonicalName() + "$ContentValues";
        try {
            Class<?> autoReleaseClass = Class.forName(autoReleaseClassName);
            Method releaseMethod = autoReleaseClass.getMethod("getContentValues", target.getClass());
            return (ContentValues) releaseMethod.invoke(null, target);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(autoReleaseClassName + " が見つかりませんでした。ビルドが壊れているか、DBColumnを使っていないクラスじゃない？", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(autoReleaseClassName + ".getContentValues が見つかりませんでした。ライブラリ壊れてない？", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
