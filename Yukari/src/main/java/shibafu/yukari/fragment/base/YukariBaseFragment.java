package shibafu.yukari.fragment.base;

import androidx.fragment.app.Fragment;

import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.linkage.ApiCollectionProvider;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.TwitterProvider;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class YukariBaseFragment extends Fragment implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
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

    public ApiCollectionProvider getApiCollectionProvider() {
        return getTwitterService();
    }

    public TwitterProvider getTwitterProvider() {
        return getTwitterService();
    }
}
