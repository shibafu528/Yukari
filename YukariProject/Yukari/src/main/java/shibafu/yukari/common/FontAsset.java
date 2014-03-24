package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import java.io.File;

/**
 * Created by Shibafu on 13/08/02.
 */
public class FontAsset {

    public static final String FONT_NAME = "VL-PGothic-Regular-Mixed4.ttf";
    public static final String FONT_ZIP = "VL-PGothic-Regular-Mixed4.zip";
    public static final String USERFONT_NAME = "userfont.ttf";
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
                if (checkUserFontExt(context)) {
                    Log.d("FontAsset", "userfont.ttfを使用します");
                    instance = new FontAsset(Typeface.createFromFile(getUserFontExtPath(context)));
                }
                else {
                    Log.d("FontAsset", "ビルトインフォントを使用します");
                    instance = new FontAsset(Typeface.createFromFile(getFontFileExtPath(context)));
                }
                if (instance.getFont() == null) {
                    throw new RuntimeException("フォント読み込みに失敗しました");
                }
            } catch (RuntimeException e) {
                Log.e("FontAsset", "Font Error!!");
                instance = new FontAsset(Typeface.DEFAULT);
            }
            Log.d("FontAsset", "Font Loaded!");
        }
        return instance;
    }

    public static boolean checkFontFileExt(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "font");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, FONT_NAME).exists();
    }

    public static boolean checkUserFontExt(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "font");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, USERFONT_NAME).exists();
    }

    public static File getFontFileExtPath(Context context) {
        return new File(new File(context.getExternalFilesDir(null), "font"), FONT_NAME);
    }

    public static File getUserFontExtPath(Context context) {
        return new File(new File(context.getExternalFilesDir(null), "font"), USERFONT_NAME);
    }

    public Typeface getFont() {
        return font;
    }
}
