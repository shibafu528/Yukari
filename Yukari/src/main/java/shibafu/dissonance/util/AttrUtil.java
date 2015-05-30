package shibafu.dissonance.util;

import android.content.res.Resources;
import android.util.TypedValue;

/**
 * Created by shibafu on 14/08/06.
 */
public class AttrUtil {
    public static int resolveAttribute(Resources.Theme theme, int resId) {
        TypedValue value = new TypedValue();
        theme.resolveAttribute(resId, value, true);
        return value.resourceId;
    }
}
