package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Created by Shibafu on 13/08/02.
 */
public class FontAsset {

    private static FontAsset instance;
    private Typeface font;

    private FontAsset() {
        instance = null;
    }
    private FontAsset(Typeface typeface) {
        font = typeface;
    }

    public static FontAsset getInstance(Context context) {
        if (instance == null) {
            instance = new FontAsset(Typeface.createFromAsset(context.getAssets(), "VL-PGothic-Regular.ttf"));
            if (instance.getFont() == null) {
                new InternalError("フォント読み込みに失敗しました").printStackTrace();
            }
        }
        return instance;
    }

    public Typeface getFont() {
        return font;
    }
}
