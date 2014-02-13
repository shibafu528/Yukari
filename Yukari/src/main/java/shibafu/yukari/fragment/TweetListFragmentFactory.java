package shibafu.yukari.fragment;

import shibafu.yukari.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static TweetListFragment create(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACE:
                return new SearchListFragment();
            default:
                return new DefaultTweetListFragment();
        }
    }
}
