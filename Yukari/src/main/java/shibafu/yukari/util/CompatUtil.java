package shibafu.yukari.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

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
}
