package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by shibafu on 15/02/07.
 */
public class TriangleView extends View {
    private int color = Color.BLACK;
    private Paint paint = new Paint();
    private Path path;

    {
        paint.setColor(this.color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    public TriangleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (path == null) {
            int height = getMeasuredHeight();
            int width = getMeasuredWidth();
            int centerHorizontal = width / 2;

            path = new Path();
            path.moveTo(centerHorizontal - width / 2, 0);
            path.lineTo(centerHorizontal - width / 2, height);
            path.lineTo(centerHorizontal + width / 2, height / 2);
            path.lineTo(centerHorizontal - width / 2, 0);
            path.close();
        }

        // draw on canvas
        canvas.drawPath(path, paint);
    }
}
