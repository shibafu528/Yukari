package shibafu.yukari.core;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDex;
import com.squareup.leakcanary.LeakCanary;
import twitter4j.AlternativeHttpClientImpl;

/**
 * Created by shibafu on 2015/08/29.
 */
public class YukariApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_force_http1", false)) {
            AlternativeHttpClientImpl.sPreferHttp2 = false;
            AlternativeHttpClientImpl.sPreferSpdy = false;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
