package shibafu.yukari.activity;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;

import shibafu.yukari.af2015.R;
import shibafu.yukari.common.TabType;
import shibafu.yukari.fragment.tabcontent.TweetListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.fragment.tabcontent.TwitterListFragment;

/**
 * Created by Shibafu on 13/08/29.
 */
public class TraceActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            default:
                setTheme(R.style.AppDialogThemeWhenLarge);
                break;
            case "dark":
                setTheme(R.style.AppDialogThemeWhenLarge_Dark);
                break;
        }
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_parent);

        if (savedInstanceState == null) {
            TwitterListFragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_TRACE);
            Bundle args = new Bundle(getIntent().getExtras());
            args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_TRACE);
            fragment.setArguments(args);

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.frame, fragment).commit();
        }
    }
}
