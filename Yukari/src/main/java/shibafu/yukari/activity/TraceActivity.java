package shibafu.yukari.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import shibafu.yukari.R;
import shibafu.yukari.fragment.TweetListFragment;

/**
 * Created by Shibafu on 13/08/29.
 */
public class TraceActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        TweetListFragment fragment = new TweetListFragment();
        Bundle args = new Bundle(getIntent().getExtras());
        args.putInt(TweetListFragment.EXTRA_MODE, TweetListFragment.MODE_TRACE);
        fragment.setArguments(args);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frame, fragment).commit();
    }
}
