package shibafu.dissonance.fragment.tabcontent;

import android.os.Bundle;

import shibafu.dissonance.common.TabInfo;
import shibafu.dissonance.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static TwitterListFragment newInstance(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
                return new SearchListFragment();
            case TabType.TABTYPE_DM:
                return new MessageListFragment();
            case TabType.TABTYPE_BOOKMARK:
                return new BookmarkListFragment();
            case TabType.TABTYPE_HISTORY:
                return new HistoryListFragment();
            default:
                return new DefaultTweetListFragment();
        }
    }

    public static TwitterListFragment newInstance(TabInfo tabInfo) {
        TwitterListFragment fragment = TweetListFragmentFactory.newInstance(tabInfo.getType());
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
