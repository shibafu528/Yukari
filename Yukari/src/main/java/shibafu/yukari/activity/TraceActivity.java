package shibafu.yukari.activity;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;
import shibafu.yukari.R;
import shibafu.yukari.common.TabType;
import shibafu.yukari.entity.Status;
import shibafu.yukari.fragment.tabcontent.TimelineFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/08/29.
 */
public class TraceActivity extends FragmentActivity {

    public static final String EXTRA_TRACE_START = "trace_start";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light").endsWith("dark")) {
            setTheme(R.style.AppDialogThemeWhenLarge_Dark);
        } else {
            setTheme(R.style.AppDialogThemeWhenLarge);
        }
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_parent);

        if (savedInstanceState == null) {
            AuthUserRecord userRecord = (AuthUserRecord) getIntent().getSerializableExtra(TweetListFragment.EXTRA_USER);
            Status status = (Status) getIntent().getSerializableExtra(EXTRA_TRACE_START);

            StringBuilder query = new StringBuilder();
            query.append("from trace:\"");
            query.append(userRecord.ScreenName);
            query.append("/");
            query.append(status.getId());
            query.append("\"");

            Fragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_TRACE);
            Bundle args = new Bundle(getIntent().getExtras());
            args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_TRACE);
            args.putString(TimelineFragment.EXTRA_FILTER_QUERY, query.toString());
            fragment.setArguments(args);

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.frame, fragment).commit();
        }
    }
}
