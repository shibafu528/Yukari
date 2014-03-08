package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

/**
 * Created by Shibafu on 13/10/28.
 */
class ImageCache {

    private static final String DIR = "picture";
    private static int maxCacheSize = 12 * 1024 * 1024;

    private static LruCache<String, Bitmap> lruCache = new LruCache<String, Bitmap>(maxCacheSize) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getRowBytes() * value.getHeight();
        }
    };

    public static Bitmap getImage(String key, Context context) {
        return BitmapCacheUtil.getImage(key, context, DIR, lruCache);
    }

    public static void putImage(String key, Bitmap image, Context context) {
        BitmapCacheUtil.putImage(key, image, context, DIR, lruCache);
    }

}
