package shibafu.yukari.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.service.TweetReceiverService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.Twitter;

public class MainActivity extends FragmentActivity {

    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private int reqAuth;

    private TweetReceiverService service;
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        reqAuth = getResources().getInteger(R.integer.requestcode_oauth);

        //Twitterログインデータを読み込む
        twitter = TwitterUtil.getTwitterInstance(this);
        users = Arrays.asList(TwitterUtil.loadUserRecords(this));
        if (users.size() < 1) {
            startActivityForResult(new Intent(this, OAuthActivity.class), reqAuth);
        }
        else {
            addTab(users.get(0), "home:" + users.get(0).ScreenName, TweetListFragment.MODE_HOME);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TweetReceiverService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private void addTab(AuthUserRecord user, String title, int mode) {
        TweetListFragment fragment = new TweetListFragment();
        Bundle b = new Bundle();
        b.putString(TweetListFragment.EXTRA_TITLE, title);
        b.putInt(TweetListFragment.EXTRA_MODE, mode);
        b.putSerializable(TweetListFragment.EXTRA_USER, user);
        fragment.setArguments(b);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame, fragment).commit();
        setTitle(title);
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
        service.reloadUsers();
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TweetReceiverService.TweetReceiverBinder binder = (TweetReceiverService.TweetReceiverBinder) service;
            MainActivity.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
