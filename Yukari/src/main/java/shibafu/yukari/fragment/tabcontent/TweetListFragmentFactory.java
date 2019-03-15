package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;

import android.support.v4.app.Fragment;
import android.util.Log;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static Fragment newInstance(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_BOOKMARK:
                return new BookmarkListFragment();
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
            case TabType.TABTYPE_DM:
            case TabType.TABTYPE_FILTER:
            case TabType.TABTYPE_HISTORY:
            case TabType.TABTYPE_TRACE:
                return new TimelineFragment();
            default:
                Log.w("TweetListFragmentFact", "DefaultTweetListFragmentが生成されます。このクラスは今後廃止されます。(Tab Type=" + tabType + ")");
                return new DefaultTweetListFragment();
        }
    }

    private static Fragment newInstance(TabInfo tabInfo) {
        Fragment fragment = TweetListFragmentFactory.newInstance(tabInfo.getType());
        Bundle b = new Bundle();
        switch (tabInfo.getType()) {
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
            case TabType.TABTYPE_DM:
                query.append("from message");
                if (tabInfo.getBindAccount() != null) {
                    query.append(":\"").append(tabInfo.getBindAccount().ScreenName).append("\"");
                }
                break;
            case TabType.TABTYPE_SEARCH:
                query.append("from search:\"")
                        .append(tabInfo.getSearchKeyword().replaceAll("\"", "\\\\\""))
                        .append("\"");
                break;
            case TabType.TABTYPE_LIST:
                query.append("from list:\"")
                        .append(tabInfo.getBindAccount().ScreenName).append("/")
                        .append(tabInfo.getBindListId()).append("\"");
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
