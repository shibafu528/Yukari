package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.support.v4.util.LruCache;
import android.util.Log;
import shibafu.yukari.util.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by shibafu on 14/03/09.
 */
public class BitmapCache {
    public static final String PROFILE_ICON_CACHE = "icon";
    public static final String IMAGE_CACHE = "picture";

    private static final int PROFILE_ICON_CACHE_SIZE;
    private static final int IMAGE_CACHE_SIZE;

    private static Map<String, BitmapLruCache> cacheMap = new HashMap<>();
    private static Map<String, Map<String, File>> existFileCache = new HashMap<>();

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            PROFILE_ICON_CACHE_SIZE = 4 * 1024 * 1024;
            IMAGE_CACHE_SIZE = 4 * 1024 * 1024;
        } else {
            PROFILE_ICON_CACHE_SIZE = 8 * 1024 * 1024;
            IMAGE_CACHE_SIZE = 8 * 1024 * 1024;
        }

        cacheMap.put(PROFILE_ICON_CACHE, new BitmapLruCache(PROFILE_ICON_CACHE_SIZE));
        cacheMap.put(IMAGE_CACHE, new BitmapLruCache(IMAGE_CACHE_SIZE));

        existFileCache.put(PROFILE_ICON_CACHE, null);
        existFileCache.put(IMAGE_CACHE, null);
    }

    public static void initialize(final Context context) {
        class Initializer {
            Initializer init(String key) {
                Map<String, File> fileMap;
                File[] files = getCacheDir(context, key).listFiles();
                if (files != null) {
                    int length = files.length;
                    fileMap = new HashMap<>(length * 4 / 3);
                    for (int i = 0; i < length; i++) {
                        File file = files[i];
                        fileMap.put(file.getName(), file);
                    }
                } else {
                    fileMap = new HashMap<>(170);
                }
                existFileCache.put(key, fileMap);
                return this;
            }
        }
        new Initializer().init(PROFILE_ICON_CACHE)
                         .init(IMAGE_CACHE);
        Log.d("BitmapCache", "Initialized cache map");
    }

    public static void dispose() {
        for (Map.Entry<String, BitmapLruCache> entry : cacheMap.entrySet()) {
            entry.getValue().evictAll();
        }
        existFileCache.put(PROFILE_ICON_CACHE, null);
        existFileCache.put(IMAGE_CACHE, null);
        Log.d("BitmapCache", "Disposed cache map");
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
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
        File cacheFile = getCacheFile(key, cacheKey);
        //日時を更新してファイルを読み込む
        if (cacheFile != null) {
            cacheFile.setLastModified(System.currentTimeMillis());
            return BitmapFactory.decodeFile(cacheFile.getPath());
        } else return null;
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
            File cacheFile = getCacheFile(key, cacheKey);
            if (cacheFile != null) {
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
            File cacheDir = getCacheDir(context, cacheKey);
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
                    Map<String, File> fileMap = existFileCache.get(cacheKey);
                    if (fileMap != null) {
                        fileMap.put(key, cacheFile);
                    }
                }
            }
        }
    }

    private static File getCacheFile(String fileKey, String cacheKey) {
        Map<String, File> fileMap = existFileCache.get(cacheKey);
        if (fileMap != null && fileMap.containsKey(fileKey)) {
            return fileMap.get(fileKey);
        } else return null;
    }

    private static File getCacheDir(Context context, String cacheKey) {
        File cacheDir;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cacheDir = context.getExternalCacheDir();
        } else {
            cacheDir = context.getCacheDir();
        }
        return new File(cacheDir, cacheKey);
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
