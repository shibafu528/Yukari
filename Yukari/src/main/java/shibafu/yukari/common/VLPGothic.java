package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Created by Shibafu on 13/08/02.
 */
public class VLPGothic {

    private static VLPGothic instance;
    private Typeface font;

    private VLPGothic() {
        instance = null;
    }
    private VLPGothic(Typeface typeface) {
        font = typeface;
    }

    public static VLPGothic getInstance(Context context) {
        if (instance == null) {
            instance = new VLPGothic(Typeface.createFromAsset(context.getAssets(), "VL-PGothic-Symbola.ttf"));
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
