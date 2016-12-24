package shibafu.yukari.util;

import android.content.res.Resources;
import android.support.annotation.AnyRes;
import android.support.annotation.AttrRes;
import android.util.TypedValue;

/**
 * Created by shibafu on 14/08/06.
 */
public class AttrUtil {
    @AnyRes
    public static int resolveAttribute(Resources.Theme theme, @AttrRes int resId) {
        TypedValue value = new TypedValue();
        theme.resolveAttribute(resId, value, true);
        return value.resourceId;
    }
}
