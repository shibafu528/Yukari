package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;

import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;

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
            case TabType.TABTYPE_FILTER:
                return new FilterListFragment();
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
            case TabType.TABTYPE_FILTER:
                b.putString(FilterListFragment.EXTRA_FILTER_QUERY, tabInfo.getFilterQuery());
                break;
        }
        b.putLong(TweetListFragment.EXTRA_ID, tabInfo.getId());
        b.putString(TweetListFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        return fragment;
    }
}
