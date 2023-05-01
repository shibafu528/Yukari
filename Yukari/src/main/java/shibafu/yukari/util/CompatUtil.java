package shibafu.yukari.util;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by shibafu on 15/06/03.
 */
public class CompatUtil {
    /**
     * 空のPengingIntentを取得します。
     * Android 2.3以下で通知にcontentIntentを設定しないとクラッシュする場合に使用します。
     * @param context ApplicationContext
     * @return 空のPengingIntent
     */
    public static PendingIntent getEmptyPendingIntent(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    }

    // https://stackoverflow.com/a/55842542
    public static String getProcessName() {
        if (Build.VERSION.SDK_INT >= 28)
            return Application.getProcessName();

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API

        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            @SuppressLint("DiscouragedPrivateApi")
            Method getProcessName = activityThread.getDeclaredMethod("currentProcessName");
            return (String) getProcessName.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
