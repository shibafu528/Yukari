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

    /**
     * メモリキャッシュから画像を取得します。<br>
     * メモリ上に存在していない場合は null を返します。
     * @param key ファイルキー
     * @param cacheKey キャッシュ分類キー(e.g. {@link #IMAGE_CACHE}, {@link #PROFILE_ICON_CACHE})
     * @return キャッシュされたBitmap
     */
    public static Bitmap getImageFromMemory(String key, String cacheKey) {
        key = StringUtil.generateKey(key);
        return cacheMap.get(cacheKey).get(key);
    }

    /**
     * ディスクキャッシュから画像を取得します。<br>
     * ディスク上に存在していない場合は null を返します。
     * @param key ファイルキー
     * @param cacheKey キャッシュ分類キー(e.g. {@link #IMAGE_CACHE}, {@link #PROFILE_ICON_CACHE})
     * @param context Context
     * @return キャッシュされたBitmap
     */
    public static Bitmap getImageFromDisk(String key, String cacheKey, Context context) {
        key = StringUtil.generateKey(key);
        File cacheFile = getCacheFile(context, key, cacheKey);
        if (cacheFile.exists()) {
            //存在していたら日時を更新してファイルを読み込む
            cacheFile.setLastModified(System.currentTimeMillis());
            return BitmapFactory.decodeFile(cacheFile.getPath());
        }
        return null;
    }

    /**
     * メモリキャッシュ、ディスクキャッシュの順に画像の取得を試みます。<br>
     * いずれにも存在していない場合は null を返します。<br>
     * ディスクI/Oがボトルネックになる可能性があります。
     * @param key ファイルキー
     * @param cacheKey キャッシュ分類キー(e.g. {@link #IMAGE_CACHE}, {@link #PROFILE_ICON_CACHE})
     * @param context Context
     * @return キャッシュされたBitmap
     */
    public static Bitmap getImage(String key, String cacheKey, Context context) {
        key = StringUtil.generateKey(key);
        //メモリ上のキャッシュから取得を試みる
        Bitmap image = cacheMap.get(cacheKey).get(key);
        if (image == null && context != null) {
            //無かったらファイルから取得を試みる
            File cacheFile = getCacheFile(context, key, cacheKey);
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

        key = StringUtil.generateKey(key);
        //メモリ上のキャッシュと、ファイルに書き込む
        cacheMap.get(cacheKey).put(key, image);
        if (context != null) {
            File cacheFile = getCacheFile(context, key, cacheKey);
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

    private static File getCacheFile(Context context, String fileKey, String cacheKey) {
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
        return new File(cacheDir, fileKey);
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
