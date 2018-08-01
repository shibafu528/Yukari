package shibafu.yukari.core;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.squareup.leakcanary.LeakCanary;
import twitter4j.AlternativeHttpClientImpl;

/**
 * Created by shibafu on 2015/08/29.
 */
public class YukariApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ProviderInstaller.installIfNeeded(this);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_force_http1", false)) {
            AlternativeHttpClientImpl.sPreferHttp2 = false;
            AlternativeHttpClientImpl.sPreferSpdy = false;
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
