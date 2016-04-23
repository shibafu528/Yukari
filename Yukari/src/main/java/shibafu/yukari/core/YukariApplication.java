package shibafu.yukari.core;

import android.app.Application;
import android.preference.PreferenceManager;
import info.shibafu528.yukari.exvoice.MRuby;
import lombok.Getter;

/**
 * Created by shibafu on 2015/08/29.
 */
public class YukariApplication extends Application {

    @Getter
    private MRuby mRuby;

    @Override
    public void onCreate() {
        super.onCreate();

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        mRuby = new MRuby(getAssets());
        mRuby.loadString("Android.require_assets 'bootstrap.rb'");
    }
}
