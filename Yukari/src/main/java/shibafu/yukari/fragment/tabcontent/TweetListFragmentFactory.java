package shibafu.yukari.fragment.tabcontent;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;

/**
 * Created by shibafu on 14/02/13.
 */
public class TweetListFragmentFactory {

    public static Fragment newInstance(int tabType) {
        switch (tabType) {
            case TabType.TABTYPE_BOOKMARK:
                return new BookmarkTimelineFragment();
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
            case TabType.TABTYPE_DM:
            case TabType.TABTYPE_FILTER:
            case TabType.TABTYPE_HISTORY:
            case TabType.TABTYPE_TRACE:
                return new TimelineFragment();
            default:
                throw new RuntimeException("DefaultTweetListFragmentが生成されようとしました。このクラスは廃止されています。(Tab Type=" + tabType + ")");
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
        b.putLong(TimelineFragment.EXTRA_ID, tabInfo.getId());
        b.putString(TimelineFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TimelineFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TimelineFragment.EXTRA_USER, tabInfo.getBindAccount());
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
                if (tabInfo.getBindAccount() == null) {
                    query.append("where (nil)");
                } else {
                    query.append("from list:\"")
                            .append(tabInfo.getBindAccount().ScreenName).append("/")
                            .append(tabInfo.getBindListId()).append("\"");
                }
                break;
            default:
                return newInstance(tabInfo);
        }
        Bundle b = new Bundle();
        b.putString(TimelineFragment.EXTRA_FILTER_QUERY, query.toString());
        b.putLong(TimelineFragment.EXTRA_ID, tabInfo.getId());
        b.putString(TimelineFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TimelineFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TimelineFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        return fragment;
    }
}
