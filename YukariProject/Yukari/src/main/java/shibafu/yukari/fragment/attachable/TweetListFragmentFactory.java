package shibafu.yukari.fragment.attachable;

import shibafu.yukari.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static TweetListFragment newInstance(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
                return new SearchListFragment();
            default:
                return new DefaultTweetListFragment();
        }
    }
}
