package shibafu.yukari.activity.base;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;

import shibafu.yukari.af2015.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ActionBarYukariBase extends ActionBarActivity implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
    private TwitterServiceConnection servicesConnection = new TwitterServiceConnection(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.YukariLightTheme);
                break;
            case "dark":
                setTheme(R.style.YukariDarkTheme);
                break;
        }
        super.onCreate(savedInstanceState);
    }

    protected void onCreate(Bundle savedInstanceState, boolean ignoreAutoTheme) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        servicesConnection.connect(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        servicesConnection.disconnect(this);
    }

    @Override
    public boolean isTwitterServiceBound() {
        return servicesConnection.isServiceBound();
    }

    @Override
    public TwitterService getTwitterService() {
        return servicesConnection.getTwitterService();
    }
}
