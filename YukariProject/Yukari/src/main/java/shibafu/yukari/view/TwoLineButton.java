package shibafu.yukari.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/08/10.
 */
public class TwoLineButton extends LinearLayout {

    private TextView tvMain, tvSub;
    private ImageView ivIcon;

    public TwoLineButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        View layout = LayoutInflater.from(context).inflate(R.layout.view_2linebutton, this);

        if (!isInEditMode()) {
            tvMain = (TextView) layout.findViewById(R.id.lineButtonText);
            tvSub = (TextView) layout.findViewById(R.id.lineButtonCount);
            ivIcon = (ImageView) layout.findViewById(R.id.lineButtonImage);

            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TwoLineButton);
            tvMain.setText(ta.getString(R.styleable.TwoLineButton_textTop));
            tvSub.setText(ta.getString(R.styleable.TwoLineButton_textBottom));
            ivIcon.setImageResource(ta.getResourceId(R.styleable.TwoLineButton_image, R.drawable.ic_draft_tweet));
            ta.recycle();
        }
    }

    public void setImageResource(int resId) {
        ivIcon.setImageResource(resId);
    }

    public void setImageBitmap(Bitmap bitmap) {
        ivIcon.setImageBitmap(bitmap);
    }

    public void setTextTop(CharSequence text) {
        tvMain.setText(text);
    }

    public void setTextBottom(CharSequence text) {
        tvSub.setText(text);
    }

}
