package shibafu.yukari.fragment.attachable;

import android.os.Bundle;

import shibafu.yukari.common.TabInfo;
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

    public static TweetListFragment newInstance(TabInfo tabInfo) {
        TweetListFragment fragment = TweetListFragmentFactory.newInstance(tabInfo.getType());
        Bundle b = new Bundle();
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_SEARCH:
                b.putString(SearchListFragment.EXTRA_SEARCH_QUERY, tabInfo.getSearchKeyword());
                break;
            case TabType.TABTYPE_LIST:
                b.putLong(DefaultTweetListFragment.EXTRA_LIST_ID, tabInfo.getBindListId());
                break;
        }
        b.putString(TweetListFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        return fragment;
    }
}
