package shibafu.yukari.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import shibafu.yukari.common.bitmapcache.BitmapCache;

/**
 * Created by shibafu on 14/03/04.
 */
public class CacheCleanerService extends IntentService {

    private static final Comparator<File> COMPARATOR = (lhs, rhs) -> {
        long sub = rhs.lastModified() - lhs.lastModified();
        if      (sub > 0)  return 1;
        else if (sub == 0) return 0;
        else               return -1;
    };

    public CacheCleanerService() {
        super("CacheCleanerService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        File cacheRoot;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cacheRoot = getExternalCacheDir();
        }
        else {
            cacheRoot = getCacheDir();
        }
        String[] categories = {
                BitmapCache.PROFILE_ICON_CACHE,
                BitmapCache.IMAGE_CACHE,
                "preview"};
        int[] limits = {
                getIntPref(sp, "pref_icon_cache_size", 4),
                getIntPref(sp, "pref_picture_cache_size", 8),
                getIntPref(sp, "pref_preview_cache_size", 16)
        };
        List<File> expirations = new ArrayList<>();
        for (int i = 0; i < categories.length; ++i) {
            expirations.addAll(findExpirationCaches(new File(cacheRoot, categories[i]), limits[i] * 1024 * 1024));
        }
        File[] tmpFiles = getExternalCacheDir().listFiles((dir, filename) -> {
            return filename.endsWith(".tmp");
        });
        expirations.addAll(Arrays.asList(tmpFiles));

        for (File f : expirations) {
            Log.d("CacheCleanerService", "Deleting: " + f.getAbsolutePath());
            f.delete();
        }
    }

    private List<File> findExpirationCaches(File dir, long limitBytes) {
        List<File> expiration = new ArrayList<>();
        File[] filesArray = dir.listFiles();
        if (filesArray != null) {
            List<File> files = Arrays.asList(filesArray);
            Collections.sort(files, COMPARATOR);

            long totalSize = 0;
            for (File f : files) {
                if (f.isFile()) {
                    long l = f.length();
                    if (totalSize + l > limitBytes) {
                        expiration.add(f);
                    }
                    totalSize += l;
                }
            }
        }
        return expiration;
    }

    private int getIntPref(SharedPreferences sp, String key, int defaultValues) {
        String s = sp.getString(key, "*");
        return "*".equals(s)? defaultValues : Integer.valueOf(s);
    }
}
