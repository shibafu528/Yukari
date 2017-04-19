package shibafu.yukari.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.CentralDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
        File[] tmpFiles = getExternalCacheDir().listFiles((dir, filename) -> filename.endsWith(".tmp"));
        expirations.addAll(Arrays.asList(tmpFiles));

        for (File f : expirations) {
            Log.d("CacheCleanerService", "Deleting: " + f.getAbsolutePath());
            f.delete();
        }

        cleanupOrphanAttachment();
    }

    private List<File> findExpirationCaches(File dir, long limitBytes) {
        List<File> expiration = new ArrayList<>();
        File[] filesArray = dir.listFiles();
        if (filesArray != null) {
            List<File> files = Arrays.asList(filesArray);
            try {
                Collections.sort(files, COMPARATOR);
            } catch (IllegalArgumentException ex) {
                /*
                API 19以上で落ちることがあるが、失敗しても無理やり適当に処理する
                IllegalArgumentException: Comparison method violates its general contract!
                    at java.util.TimSort.mergeHi(TimSort.java:864)
                    at java.util.TimSort.mergeAt(TimSort.java:481)
                    at java.util.TimSort.mergeCollapse(TimSort.java:404)
                    at java.util.TimSort.sort(TimSort.java:210)
                    at java.util.TimSort.sort(TimSort.java:169)
                    at java.util.Arrays.sort(Arrays.java:2023)
                    at java.util.Collections.sort(Collections.java:1883)
                 */
                ex.printStackTrace();
            }

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

    private void cleanupOrphanAttachment() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return;
        }

        File attachesDir = new File(getExternalFilesDir(null), "attaches");
        String attachesDirUri = Uri.fromFile(attachesDir).toString();

        // 添付として利用中のUriを抽出
        List<String> usingUris = new ArrayList<>();
        CentralDatabase db = new CentralDatabase(getApplicationContext()).open();
        try {
            usingUris = Stream.of(db.getDrafts())
                    .flatMap(draft -> Stream.of(draft.getAttachedPictures()))
                    .map(uri -> uri.toString())
                    .distinct()
                    .filter(uri -> uri.contains(attachesDirUri))
                    .collect(Collectors.toList());
        } finally {
            db.close();
        }

        // 使用中でなく、かつ3日以上前のデータを削除
        long before1Day = System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS * 3;
        for (File file : attachesDir.listFiles()) {
            if (file.lastModified() < before1Day) {
                String fileUri = Uri.fromFile(file).toString();
                if (!usingUris.contains(fileUri)) {
                    Log.d("CacheCleanerService", "Deleting: " + file.getAbsolutePath());
                    file.delete();
                }
            }
        }
    }
}
