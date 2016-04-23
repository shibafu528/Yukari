package shibafu.yukari.fragment.base;

import android.content.Context;
import android.support.v4.app.ListFragment;

import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.util.AppContext;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ListTwitterFragment extends ListFragment implements AppContext, TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
    private TwitterServiceConnection servicesConnection = new TwitterServiceConnection(this);

    @Override
    public void onStart() {
        super.onStart();
        servicesConnection.connect(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        servicesConnection.disconnect(getActivity());
    }

    @Override
    public Context getApplicationContext() {
        if (getActivity() == null) {
            return null;
        }
        return getActivity().getApplicationContext();
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
