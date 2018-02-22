package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;

import android.support.v4.app.Fragment;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static Fragment newInstance(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
                return new SearchListFragment();
            case TabType.TABTYPE_DM:
                return new MessageListFragment();
            case TabType.TABTYPE_BOOKMARK:
                return new BookmarkListFragment();
            case TabType.TABTYPE_FILTER:
            case TabType.TABTYPE_HISTORY:
            case TabType.TABTYPE_DON_PUBLIC:
                return new TimelineFragment();
            default:
                return new DefaultTweetListFragment();
        }
    }

    private static Fragment newInstance(TabInfo tabInfo) {
        Fragment fragment = TweetListFragmentFactory.newInstance(tabInfo.getType());
        Bundle b = new Bundle();
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_SEARCH:
                b.putString(SearchListFragment.EXTRA_SEARCH_QUERY, tabInfo.getSearchKeyword());
                break;
            case TabType.TABTYPE_LIST:
                b.putLong(DefaultTweetListFragment.EXTRA_LIST_ID, tabInfo.getBindListId());
                break;
            case TabType.TABTYPE_FILTER:
                b.putString(TimelineFragment.EXTRA_FILTER_QUERY, tabInfo.getFilterQuery());
                break;
            case TabType.TABTYPE_HISTORY:
                b.putString(TimelineFragment.EXTRA_FILTER_QUERY, "where (nil)");
                break;
        }
        b.putLong(TweetListFragment.EXTRA_ID, tabInfo.getId());
        b.putString(TweetListFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        return fragment;
    }

    public static Fragment newInstanceWithFilter(TabInfo tabInfo) {
        Fragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER);
        StringBuilder query = new StringBuilder();
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_HOME:
                query.append("from home");
                if (tabInfo.getBindAccount() != null) {
                    query.append(":\"").append(tabInfo.getBindAccount().ScreenName).append("\"");
                }
                break;
            case TabType.TABTYPE_MENTION:
                query.append("from mention");
                if (tabInfo.getBindAccount() != null) {
                    query.append(":\"").append(tabInfo.getBindAccount().ScreenName).append("\"");
                }
                break;
            default:
                return newInstance(tabInfo);
        }
        Bundle b = new Bundle();
        b.putString(TimelineFragment.EXTRA_FILTER_QUERY, query.toString());
        b.putLong(TweetListFragment.EXTRA_ID, tabInfo.getId());
        b.putString(TweetListFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        return fragment;
    }
}
