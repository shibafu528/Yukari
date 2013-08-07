package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by Shibafu on 13/08/02.
 */
public class FontAsset {

    public static final String FONT_NAME = "VL-PGothic-Regular-Mixed4.ttf";
    public static final String FONT_ZIP = "VL-PGothic-Regular-Mixed4.zip";
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
            try {
                instance = new FontAsset(Typeface.createFromFile(getFontFileExtPath(context)));
                if (instance.getFont() == null) {
                    throw new RuntimeException("フォント読み込みに失敗しました");
                }
            } catch (RuntimeException e) {
                Log.e("FontAsset", "Font Error!!");
            }
            Log.d("FontAsset", "Font Loaded!");
        }
        return instance;
    }

    public static boolean checkFontFileExt(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "font");
        if (!dir.exists()) {
            dir.mkdir();
        }

        return new File(dir, FONT_NAME).exists();
    }

    public static File getFontFileExtPath(Context context) {
        return new File(new File(context.getExternalFilesDir(null), "font"), FONT_NAME);
    }

    public Typeface getFont() {
        return font;
    }
}
