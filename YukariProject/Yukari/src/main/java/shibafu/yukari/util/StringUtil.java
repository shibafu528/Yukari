package shibafu.yukari.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by Shibafu on 14/01/12.
 */
public class StringUtil {
    public static String encodeKey(String key) {
        try {
            return URLEncoder.encode(key, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return key;
        }
    }
}
