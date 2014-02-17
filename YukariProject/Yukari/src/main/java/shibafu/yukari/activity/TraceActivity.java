package shibafu.yukari.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;

import shibafu.yukari.R;
import shibafu.yukari.common.TabType;
import shibafu.yukari.fragment.attachable.TweetListFragment;
import shibafu.yukari.fragment.attachable.TweetListFragmentFactory;

/**
 * Created by Shibafu on 13/08/29.
 */
public class TraceActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_parent);

        TweetListFragment fragment = TweetListFragmentFactory.getInstance(TabType.TABTYPE_TRACE);
        Bundle args = new Bundle(getIntent().getExtras());
        args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_TRACE);
        fragment.setArguments(args);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frame, fragment).commit();
    }
}
