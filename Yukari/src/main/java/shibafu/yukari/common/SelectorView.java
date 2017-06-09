package shibafu.yukari.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import shibafu.yukari.R;

/**
 * Created by shibafu on 15/03/29.
 */
public class SelectorView extends FrameLayout {

    private StateListDrawable background;

    public SelectorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.SelectorView);
        Drawable onPressed = array.getDrawable(R.styleable.SelectorView_on_pressed);
        Drawable onFocused = array.getDrawable(R.styleable.SelectorView_on_focused);
        background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, onPressed);
        background.addState(new int[]{android.R.attr.state_focused}, onFocused);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        array.recycle();
    }

    @Override
    public void requestLayout() {
        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).setBackgroundDrawable(background);
        }
        super.requestLayout();
    }
}
