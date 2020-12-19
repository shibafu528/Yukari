package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.StatusChildUI;
import shibafu.yukari.common.StatusUI;
import shibafu.yukari.entity.Status;
import shibafu.yukari.fragment.status.StatusActionFragment;
import shibafu.yukari.fragment.status.StatusLinkFragment;
import shibafu.yukari.fragment.status.StatusMainFragment;
import shibafu.yukari.fragment.tabcontent.TimelineFragment;
import shibafu.yukari.mastodon.entity.DonStatus;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.view.DonStatusView;
import shibafu.yukari.view.StatusView;
import shibafu.yukari.view.TweetView;

import java.util.List;

public class StatusActivity extends ActionBarYukariBase implements StatusUI {

    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_USER = "user";

    private AuthUserRecord user = null;
    private Status status = null;

    private StatusView tweetView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.ColorsTheme_Light_Translucent);
                break;
            case "dark":
                setTheme(R.style.ColorsTheme_Dark_Translucent);
                break;
            case "akari":
                setTheme(R.style.ColorsTheme_Akari_Translucent);
                break;
            case "akari_dark":
                setTheme(R.style.ColorsTheme_Akari_Dark_Translucent);
                break;
            case "zunko":
                setTheme(R.style.ColorsTheme_Zunko_Translucent);
                break;
            case "zunko_dark":
                setTheme(R.style.ColorsTheme_Zunko_Dark_Translucent);
                break;
            case "maki":
                setTheme(R.style.ColorsTheme_Maki_Translucent);
                break;
            case "maki_dark":
                setTheme(R.style.ColorsTheme_Maki_Dark_Translucent);
                break;
            case "aoi":
                setTheme(R.style.ColorsTheme_Aoi_Translucent);
                break;
            case "aoi_dark":
                setTheme(R.style.ColorsTheme_Aoi_Dark_Translucent);
                break;
            case "akane":
                setTheme(R.style.ColorsTheme_Akane_Translucent);
                break;
            case "akane_dark":
                setTheme(R.style.ColorsTheme_Akane_Dark_Translucent);
                break;
        }
        super.onCreate(savedInstanceState, true);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_status);

        findViewById(android.R.id.content).setOnClickListener(v -> finish());

        Intent args = getIntent();
        Object anyStatus = args.getSerializableExtra(EXTRA_STATUS);
        if (anyStatus instanceof Status) {
            status = (Status) anyStatus;
        } else if (anyStatus instanceof PreformedStatus) {
            status = new TwitterStatus((PreformedStatus) anyStatus, ((PreformedStatus) anyStatus).getRepresentUser());
        } else {
            throw new ClassCastException(anyStatus.getClass().getName());
        }
        if (savedInstanceState != null) {
            user = (AuthUserRecord) savedInstanceState.getSerializable(EXTRA_USER);
        } else {
            user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);
        }

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

        FrameLayout statusViewFrame = (FrameLayout) findViewById(R.id.status_tweet);
        if (status instanceof TwitterStatus) {
            tweetView = new TweetView(this);
        } else if (status instanceof DonStatus) {
            tweetView = new DonStatusView(this);
        } else {
            throw new IllegalArgumentException(EXTRA_STATUS);
        }
        tweetView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (user != null) {
            tweetView.setUserRecords(user.toSingleList());
        }
        tweetView.setMode(StatusView.Mode.DETAIL);
        tweetView.setStatus(status);
        if (status.getOriginStatus().getInReplyToId() > 0) {
            tweetView.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), TraceActivity.class);
                intent.putExtra(TimelineFragment.EXTRA_USER, user);
                intent.putExtra(TimelineFragment.EXTRA_TITLE, "Trace");
                intent.putExtra(TraceActivity.EXTRA_TRACE_START, status.getOriginStatus());
                startActivity(intent);
            });
        }
        statusViewFrame.addView(tweetView);

        TextView tvCounter = (TextView) findViewById(R.id.tv_state_counter);
        final int retweeted = status.getRepostsCount();
        final int faved = status.getFavoritesCount();
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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_USER, user);
    }

    @Override
    public void onServiceConnected() {
        tweetView.setUserExtras(getTwitterService().getUserExtras());
        tweetView.updateView();

        AuthUserRecord priorityUser = getTwitterService().getPriority(status.getOriginStatus().getUser().getIdenticalUrl());
        if (priorityUser != null) {
            setUserRecord(priorityUser);
        }
    }

    @Override
    public void onServiceDisconnected() {}

    @NonNull
    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public AuthUserRecord getUserRecord() {
        return user;
    }

    @Override
    public void setUserRecord(AuthUserRecord userRecord) {
        this.user = userRecord;
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (fragments != null) {
            for (Fragment fragment : fragments) {
                if (fragment instanceof StatusChildUI) {
                    ((StatusChildUI) fragment).onUserChanged(userRecord);
                }
            }
        }
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment f;
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
                default:
                    return null;
            }
            Bundle b = new Bundle();
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
