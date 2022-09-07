package shibafu.yukari.fragment.base;

import androidx.fragment.app.ListFragment;

import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ListYukariBaseFragment extends ListFragment implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
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

    public AccountManager getAccountManager() {
        return getTwitterService();
    }

    public UserExtrasManager getUserExtrasManager() {
        return getTwitterService();
    }
}
