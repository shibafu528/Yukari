package shibafu.yukari.util;

import java.lang.reflect.Array;
import java.util.Map;

/**
 * Created by shibafu on 2016/05/01.
 */
public class ObjectInspector {
    public static String inspect(Object o) {
        if (o == null) {
            return "null";
        } else if (o.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int length = Array.getLength(o);
            for (int i = 0; i < length; i++) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(inspect(Array.get(o, i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (o instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            for (Object entry : ((Map) o).entrySet()) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }

                sb.append(inspect(((Map.Entry) entry).getKey()));
                sb.append(" => ");
                sb.append(inspect(((Map.Entry) entry).getValue()));
            }
            sb.append("}");
            return sb.toString();
        } else {
            return String.format("[%s] %s", o.getClass().getSimpleName(), o.toString());
        }
    }
}
