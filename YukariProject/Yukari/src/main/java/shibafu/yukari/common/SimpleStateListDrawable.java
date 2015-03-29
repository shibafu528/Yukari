package shibafu.yukari.common;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Created by shibafu on 15/03/29.
 */
public class SimpleStateListDrawable extends StateListDrawable {

    public SimpleStateListDrawable(Drawable normal, Drawable onPressed, Drawable onFocused) {
        addState(new int[]{android.R.attr.state_pressed}, onPressed);
        addState(new int[]{android.R.attr.state_focused}, onFocused);
        addState(new int[]{}, normal);
    }
}
