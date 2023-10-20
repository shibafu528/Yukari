package shibafu.yukari.common.bitmapcache;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

public class MemoryCalculator {
    public static int calculateCacheSize(@NonNull Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int maxMemMegaBytes = activityManager.getMemoryClass();
        int quotaMegaBytes = Math.min((int) (maxMemMegaBytes * 0.4), 64);
        Log.d("MemoryCalculator", String.format("max mem: %d MB, quota: %d MB", maxMemMegaBytes, quotaMegaBytes));
        return (quotaMegaBytes / 2) * 1024 * 1024;
    }
}
