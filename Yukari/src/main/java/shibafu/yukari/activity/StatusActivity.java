package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.FragmentYukariBase;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.fragment.status.StatusActionFragment;
import shibafu.yukari.fragment.status.StatusLinkFragment;
import shibafu.yukari.fragment.status.StatusMainFragment;
import shibafu.yukari.fragment.tabcontent.DefaultTweetListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

public class StatusActivity extends FragmentYukariBase {

    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_USER = "user";

    private AuthUserRecord user = null;
    private PreformedStatus status = null;

    private View tweetView;
    private TweetAdapterWrap.ViewConverter viewConverter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.YukariLightTheme_Translucent);
                break;
            case "dark":
                setTheme(R.style.YukariDarkTheme_Translucent);
                break;
        }
        super.onCreate(savedInstanceState, true);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_status);

        findViewById(android.R.id.content).setOnClickListener(v -> finish());

        Intent args = getIntent();
        status = (PreformedStatus) args.getSerializableExtra(EXTRA_STATUS);
        user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);

        if (status == null) {
            Toast.makeText(this, "なんですかこのツイートは、読めないのですけど...", Toast.LENGTH_SHORT).show();
            finish();
        }

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setCurrentItem(1);
        mViewPager.setPageMargin(-getResources().getDimensionPixelSize(R.dimen.status_fragments_margin));
        mViewPager.setOffscreenPageLimit(3);

        tweetView = findViewById(R.id.status_tweet);
        if ((status.isRetweet() && status.getRetweetedStatus().getInReplyToStatusId() > 0)
                || status.getInReplyToStatusId() > 0) {
            tweetView.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), TraceActivity.class);
                intent.putExtra(TweetListFragment.EXTRA_USER, user);
                intent.putExtra(TweetListFragment.EXTRA_TITLE, "Trace");
                intent.putExtra(DefaultTweetListFragment.EXTRA_TRACE_START,
                        status.isRetweet()? status.getRetweetedStatus() : status);
                startActivity(intent);
            });
        }

        TextView tvCounter = (TextView) findViewById(R.id.tv_state_counter);
        int retweeted = status.isRetweet()? status.getRetweetedStatus().getRetweetCount() : status.getRetweetCount();
        int faved = status.isRetweet()? status.getRetweetedStatus().getFavoriteCount() : status.getFavoriteCount();
        String countRT = retweeted + "RT";
        String countFav = faved + "Fav";
        if (retweeted > 0 && faved > 0) {
            tvCounter.setText(countRT + " " + countFav);
            tvCounter.setVisibility(View.VISIBLE);
        }
        else if (retweeted > 0) {
            tvCounter.setText(countRT);
            tvCounter.setVisibility(View.VISIBLE);
        }
        else if (faved > 0) {
            tvCounter.setText(countFav);
            tvCounter.setVisibility(View.VISIBLE);
        }

        viewConverter = TweetAdapterWrap.ViewConverter.newInstance(
                this,
                (user != null)? user.toSingleList() : null,
                null,
                PreferenceManager.getDefaultSharedPreferences(this),
                PreformedStatus.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            new Handler().post(() -> viewConverter.convertView(tweetView, status, TweetAdapterWrap.ViewConverter.MODE_DETAIL));
        }
    }

    @Override
    public void onServiceConnected() {
        viewConverter.setUserExtras(getTwitterService().getUserExtras());
    }

    @Override
    public void onServiceDisconnected() {}

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
