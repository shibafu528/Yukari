package shibafu.dissonance.fragment.base;

import android.support.v4.app.ListFragment;

import shibafu.dissonance.service.TwitterService;
import shibafu.dissonance.service.TwitterServiceConnection;
import shibafu.dissonance.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ListTwitterFragment extends ListFragment implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
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
    public boolean isTwitterServiceBound() {
        return servicesConnection.isServiceBound();
    }

    @Override
    public TwitterService getTwitterService() {
        return servicesConnection.getTwitterService();
    }
}
