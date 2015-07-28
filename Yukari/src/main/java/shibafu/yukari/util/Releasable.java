package shibafu.yukari.util;

import java.lang.reflect.Field;

/**
 * リソースの解放が必要であることを明示し、またその手段を提供します。
 *
 * Created by shibafu on 2015/07/28.
 */
public interface Releasable {
    default void release() {
        for (Field field : getClass().getDeclaredFields()) {
            AutoRelease autoRelease = field.getAnnotation(AutoRelease.class);
            if (autoRelease != null) {
                try {
                    field.setAccessible(true);
                    field.set(this, null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
