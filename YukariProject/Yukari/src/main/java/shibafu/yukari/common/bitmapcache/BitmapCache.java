package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import shibafu.yukari.util.StringUtil;

/**
 * Created by shibafu on 14/03/09.
 */
public class BitmapCache {
    public static final String PROFILE_ICON_CACHE = "icon";
    public static final String IMAGE_CACHE = "picture";

    private static final int PROFILE_ICON_CACHE_SIZE = 8 * 1024 * 1024;
    private static final int IMAGE_CACHE_SIZE = 12 * 1024 * 2014;

    private static Map<String, BitmapLruCache> cacheMap = new HashMap<>();

    static {
        cacheMap.put(PROFILE_ICON_CACHE, new BitmapLruCache(PROFILE_ICON_CACHE_SIZE));
        cacheMap.put(IMAGE_CACHE, new BitmapLruCache(IMAGE_CACHE_SIZE));
    }

    public static Bitmap getImage(String key, Context context, String cacheKey) {
        key = StringUtil.encodeKey(key);
        //メモリ上のキャッシュから取得を試みる
        Bitmap image = cacheMap.get(cacheKey).get(key);
        if (image == null && context != null) {
            //無かったらファイルから取得を試みる
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, cacheKey);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, key);
            if (cacheFile.exists()) {
                //存在していたらファイルを読み込む
                image = BitmapFactory.decodeFile(cacheFile.getPath());
                cacheFile.setLastModified(System.currentTimeMillis());
            }
        }
        return image;
    }

    public static void putImage(String key, Bitmap image, Context context, String cacheKey) {
        if (key == null || image == null) return;

        key = StringUtil.encodeKey(key);
        //メモリ上のキャッシュと、ファイルに書き込む
        cacheMap.get(cacheKey).put(key, image);
        if (context != null) {
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, cacheKey);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, key);
            synchronized (context) {
                if (!cacheFile.exists()) {
                    //存在していなかったらファイルを書き込む
                    try {
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static class BitmapLruCache extends LruCache<String, Bitmap> {

        public BitmapLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getRowBytes() * value.getHeight();
        }
    }
}
