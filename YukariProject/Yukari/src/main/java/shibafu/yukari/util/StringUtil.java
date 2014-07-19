package shibafu.yukari.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Shibafu on 14/01/12.
 */
public class StringUtil {
    public static String generateKey(String key) {
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
}
