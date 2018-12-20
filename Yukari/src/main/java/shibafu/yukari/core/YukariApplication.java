package shibafu.yukari.core;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.preference.PreferenceManager;
import com.squareup.leakcanary.LeakCanary;
import shibafu.yukari.R;
import twitter4j.AlternativeHttpClientImpl;

/**
 * Created by shibafu on 2015/08/29.
 */
public class YukariApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_force_http1", false)) {
            AlternativeHttpClientImpl.sPreferHttp2 = false;
            AlternativeHttpClientImpl.sPreferSpdy = false;
        }

        // 通知チャンネルの作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel generalChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_general),
                    "その他の通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(generalChannel);

            NotificationChannel errorChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_error),
                    "エラーの通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(errorChannel);

            NotificationChannel coreServiceChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_core_service),
                    "常駐サービス",
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(coreServiceChannel);

            NotificationChannel asyncActionChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_async_action),
                    "バックグラウンド処理",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(asyncActionChannel);
        }

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        MultiDex.install(this);
    }
}
