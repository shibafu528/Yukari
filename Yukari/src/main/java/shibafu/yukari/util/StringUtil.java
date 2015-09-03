package shibafu.yukari.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;

import java.util.Calendar;
import java.util.Date;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 14/01/12.
 */
public class StringUtil {
    private final static int TOO_MANY_REPEAT = 4;

    public static String generateKey(String key) {
        if (key == null) return "null";
        char[] array = key.toCharArray();
        int length = array.length;
        for (int i = 0; i < length; ++i) {
            switch (array[i]) {
                case '<':
                case '>':
                case ':':
                case '*':
                case '?':
                case '"':
                case '/':
                case '\\':
                case '|':
                    array[i] = '-';
                    break;
            }
        }
        return String.valueOf(array);
    }

    public static StringBuilder format02d(StringBuilder s, int num) {
        if (num < 10) {
            s.append('0');
        }
        return s.append(num);
    }

    public static String formatDate(Date date) {
        StringBuilder s = new StringBuilder();
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        s.append(c.get(Calendar.YEAR)).append('/');
        format02d(s, c.get(Calendar.MONTH) + 1).append('/');
        format02d(s, c.get(Calendar.DAY_OF_MONTH)).append(' ');
        format02d(s, c.get(Calendar.HOUR_OF_DAY)).append(':');
        format02d(s, c.get(Calendar.MINUTE)).append(':');
        format02d(s, c.get(Calendar.SECOND));
        return s.toString();
    }

    public static String formatDate(long timeInMillis) {
        StringBuilder s = new StringBuilder();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeInMillis);
        s.append(c.get(Calendar.YEAR)).append('/');
        format02d(s, c.get(Calendar.MONTH) + 1).append('/');
        format02d(s, c.get(Calendar.DAY_OF_MONTH)).append(' ');
        format02d(s, c.get(Calendar.HOUR_OF_DAY)).append(':');
        format02d(s, c.get(Calendar.MINUTE)).append(':');
        format02d(s, c.get(Calendar.SECOND));
        return s.toString();
    }

    public static String getVersionInfo(Context context) {
        StringBuilder sb = new StringBuilder(context.getString(R.string.app_name));
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            sb.append(" ");
            sb.append(packageInfo.versionName);
            sb.append("/");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            sb.append(" unknown ver/");
        }
        sb.append(Build.MANUFACTURER);
        sb.append("/");
        sb.append(Build.MODEL);
        sb.append("/");
        sb.append(Build.VERSION.RELEASE);
        return sb.toString();
    }

    @Nullable
    public static String compressText(String text) {
        String repeatedSequence = "";

        int maxRepeat = 0;
        String[] split = text.split("\n");
        for (String s1 : split) {
            int repeat = 0;
            for (String s2 : split) {
                if (!"".equals(s2.trim()) && s1.trim().equals(s2.trim())) {
                    ++repeat;
                }
            }
            if ((maxRepeat = Math.max(maxRepeat, repeat)) == repeat) {
                repeatedSequence = s1.trim();
            }
        }
        if (maxRepeat >= TOO_MANY_REPEAT) {
            return repeatedSequence;
        } else {
            return null;
        }
    }
}
