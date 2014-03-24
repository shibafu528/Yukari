package shibafu.yukari.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.Window;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.fragment.StatusActionFragment;
import shibafu.yukari.fragment.StatusLinkFragment;
import shibafu.yukari.fragment.StatusMainFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;

public class StatusActivity extends FragmentActivity {

    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_USER = "user";

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;

    private AuthUserRecord user = null;
    private PreformedStatus status = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_status);

        Intent args = getIntent();
        status = (PreformedStatus) args.getSerializableExtra(EXTRA_STATUS);
        user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);

        if (status == null) {
            Toast.makeText(this, "なんですかこのツイートは、読めないのですけど...", Toast.LENGTH_SHORT).show();
            finish();
        }

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setCurrentItem(1);

        PagerTabStrip pagerTabStrip = (PagerTabStrip) findViewById(R.id.pager_title_strip);
        pagerTabStrip.setDrawFullUnderline(true);
        pagerTabStrip.setTabIndicatorColor(Color.parseColor("#FFFFFF"));

    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment f = null;
            Bundle b = new Bundle();
            switch (position) {
                case 0:
                    f = new StatusLinkFragment();
                    break;
                case 1:
                    f = new StatusMainFragment();
                    break;
                case 2:
                    f = new StatusActionFragment();
                    break;
            }
            b.putSerializable(EXTRA_STATUS, status);
            b.putSerializable(EXTRA_USER, user);
            f.setArguments(b);
            return f;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Link";
                case 1:
                    return "Status";
                case 2:
                    return "Action";
            }
            return null;
        }
    }

}
