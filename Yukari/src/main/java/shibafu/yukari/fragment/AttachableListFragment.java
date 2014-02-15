package shibafu.yukari.fragment;

import android.support.v4.app.ListFragment;

import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/09/01.
 */
public abstract class AttachableListFragment extends ListFragment {
    public abstract String getTitle();
    public abstract AuthUserRecord getCurrentUser();
    public abstract void scrollToTop();
    public abstract void scrollToBottom();
    public abstract boolean isCloseable();
}
