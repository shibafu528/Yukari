package shibafu.yukari.util;

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
}
