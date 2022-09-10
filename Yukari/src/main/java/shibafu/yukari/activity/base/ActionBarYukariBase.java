package shibafu.yukari.activity.base;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.util.ThemeUtil;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ActionBarYukariBase extends AppCompatActivity implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
    private TwitterServiceConnection servicesConnection = new TwitterServiceConnection(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (allowAutoTheme()) {
            ThemeUtil.setActivityTheme(this);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * @deprecated Override {@link #allowAutoTheme()} and return false.
     */
    @Deprecated
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

    public AccountManager getAccountManager() {
        return getTwitterService();
    }

    public UserExtrasManager getUserExtrasManager() {
        return getTwitterService();
    }

    protected boolean allowAutoTheme() {
        return true;
    }
}
