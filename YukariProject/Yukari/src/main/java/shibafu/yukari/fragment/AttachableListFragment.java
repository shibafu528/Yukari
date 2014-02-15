package shibafu.yukari.fragment;

import android.support.v4.app.ListFragment;

import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/09/01.
 */
public abstract class AttachableListFragment extends ListFragment {
    public abstract String getTitle();
    public abstract AuthUserRecord getCurrentUser();
    public abstract boolean isCloseable();

    public void scrollToTop() {
        getListView().setSelection(0);
    }

    public void scrollToBottom() {
        getListView().setSelection(getListAdapter().getCount() - 1);
    }
}
