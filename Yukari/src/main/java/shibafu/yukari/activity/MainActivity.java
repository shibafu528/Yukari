package shibafu.yukari.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Twitter;

public class MainActivity extends FragmentActivity {

    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private int reqAuth;

    private List<TweetListFragment> tabs = new ArrayList<TweetListFragment>();
    private ViewPager pager;
    private PagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        reqAuth = getResources().getInteger(R.integer.requestcode_oauth);

        //Twitterログインデータを読み込む
        twitter = TwitterUtil.getTwitterInstance(this);
        users = Arrays.asList(TwitterUtil.loadUserRecords(this));
        if (users.size() < 1) {
            startActivityForResult(new Intent(this, OAuthActivity.class), reqAuth);
        }

        //ViewPagerを準備する
        pager = (ViewPager) findViewById(R.id.pager);
        adapter = new PagerAdapter(getSupportFragmentManager());
        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        tabStrip.setDrawFullUnderline(true);
        tabStrip.setTabIndicatorColor(Color.parseColor("#EE879F"));

        //タブ初期化
        initTabs();
        pager.setAdapter(adapter);
        pager.setCurrentItem(0);
    }

    private void initTabs() {
        for (AuthUserRecord aur : users) {
            Log.d("initTabs", "user:" + aur.ScreenName);
            addTab(aur, "home:" + aur.ScreenName, TweetListFragment.MODE_HOME);
        }
    }

    private void addTab(AuthUserRecord user, String title, int mode) {
        TweetListFragment fragment = new TweetListFragment();
        Bundle b = new Bundle();
        b.putString(TweetListFragment.EXTRA_TITLE, title);
        b.putInt(TweetListFragment.EXTRA_MODE, mode);
        b.putSerializable(TweetListFragment.EXTRA_USER, user);
        fragment.setArguments(b);
        tabs.add(fragment);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == reqAuth) {
            switch (resultCode) {
                case RESULT_OK:
                    //認証情報をロードし差分を追加する
                    reloadUsers();
                    break;
                default:
                    break;
            }
        }
    }

    private void reloadUsers() {
        AuthUserRecord[] newestList = TwitterUtil.loadUserRecords(this);
        for (AuthUserRecord aur : newestList) {
            if (!users.contains(aur)) {
                users.add(aur);
                addTab(aur, "home:" + aur.ScreenName, TweetListFragment.MODE_HOME);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {

        public PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return tabs.get(i);
        }

        @Override
        public int getCount() {
            return tabs.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabs.get(position).getTitle();
        }
    }
}
