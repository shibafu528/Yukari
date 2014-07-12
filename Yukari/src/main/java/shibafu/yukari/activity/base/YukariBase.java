package shibafu.yukari.activity.base;

import android.app.Activity;

import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class YukariBase extends Activity implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
    private TwitterServiceConnection servicesConnection = new TwitterServiceConnection(this);

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
