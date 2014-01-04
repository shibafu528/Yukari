package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.AttachableList;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

public class MainActivity extends FragmentActivity implements TwitterServiceDelegate {

    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_FRIEND_CACHE = 2;

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isKeepScreenOn = false;

    private boolean isTouchTweet = false;
    private float tweetGestureYStart = 0;
    private float tweetGestureY = 0;

    private TweetListFragment currentPage;
    private List<AttachableList> pageList = new ArrayList<AttachableList>();
    private TextView tvTabText;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "[Yukari 起動エラー] ストレージエラー\nアプリの動作にはストレージが必須です", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        else if (!FontAsset.checkFontFileExt(this)) {
            Intent intent = new Intent(this, AssetExtractActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        tvTabText = (TextView) findViewById(R.id.tvMainTab);
        tvTabText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
                Menu menu = popupMenu.getMenu();
                AttachableList page;
                for (int i = 0; i < pageList.size(); ++i) {
                    page = pageList.get(i);
                    menu.add(Menu.NONE, i, Menu.NONE, page.getTitle());
                }
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        viewPager.setCurrentItem(menuItem.getItemId(), true);
                        return true;
                    }
                });
                popupMenu.show();
            }
        });
        tvTabText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
                Menu menu = popupMenu.getMenu();
                menu.add(Menu.NONE, 0, Menu.NONE, "⇧ TLの一番上へ");
                menu.add(Menu.NONE, 1, Menu.NONE, "⇩ TLの一番下へ");
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case 0:
                                currentPage.scrollToTop();
                                return true;
                            case 1:
                                currentPage.scrollToBottom();
                                return true;
                        }
                        return false;
                    }
                });
                popupMenu.show();
                return true;
            }
        });

        ImageButton ibSearch = (ImageButton) findViewById(R.id.ibSearch);
        ibSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
                popupMenu.inflate(R.menu.search);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_show_user:
                            {
                                final EditText tvInput = new EditText(MainActivity.this);
                                tvInput.setHint("@screen_name (@省略可)");

                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle("プロフィールを直接開く");
                                builder.setView(tvInput);
                                builder.setPositiveButton("開く", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();

                                        String sn = tvInput.getText().toString();
                                        if (sn.startsWith("@")) {
                                            sn = sn.substring(1);
                                        }

                                        AsyncTask<String, Void, User> task = new AsyncTask<String, Void, User>() {
                                            @Override
                                            protected User doInBackground(String... params) {
                                                try {
                                                    User user = service.getTwitter().showUser(params[0]);
                                                    return user;
                                                } catch (TwitterException e) {
                                                    e.printStackTrace();
                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onPostExecute(User user) {
                                                if (user != null) {
                                                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                                    intent.putExtra(ProfileActivity.EXTRA_TARGET, user.getId());
                                                    intent.putExtra(ProfileActivity.EXTRA_USER, serviceBound?service.getPrimaryUser():null);
                                                    startActivity(intent);
                                                }
                                                else {
                                                    Toast.makeText(MainActivity.this, "ユーザー検索エラー", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        };
                                        task.execute(sn);
                                    }
                                });
                                builder.setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                                AlertDialog ad = builder.create();
                                ad.show();
                                break;
                            }
                            case R.id.action_find_name:
                                startActivityForResult(new Intent(MainActivity.this, SNPickerActivity.class), REQUEST_FRIEND_CACHE);
                                break;
                        }
                        return false;
                    }
                });

                popupMenu.show();
            }
        });

        FrameLayout area = (FrameLayout) findViewById(R.id.tweetgesture);
        area.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        tweetGestureYStart = event.getY();
                    case MotionEvent.ACTION_MOVE:
                        tweetGestureY = event.getY();
                        isTouchTweet = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (isTouchTweet && Math.abs(tweetGestureYStart - tweetGestureY) > 80) {
                            Intent intent = new Intent(MainActivity.this, TweetActivity.class);
                            startActivity(intent);
                        }
                        break;
                }
                return true;
            }
        });

        ImageButton ibMenu = (ImageButton) findViewById(R.id.ibMenu);
        ibMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MenuDialogFragment menuDialogFragment = new MenuDialogFragment();
                menuDialogFragment.show(getSupportFragmentManager(), "menu");
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (ViewConfiguration.get(this).hasPermanentMenuKey()) {
                ibMenu.setVisibility(View.GONE);
            }
        }
        else {
            ibMenu.setVisibility(View.GONE);
        }

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {

            }

            @Override
            public void onPageSelected(int i) {
                tvTabText.setText(pageList.get(i).getTitle());
                currentPage = (TweetListFragment) pageList.get(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        //スリープ防止設定
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        setKeepScreenOn(sp.getBoolean("pref_boot_screenon", false));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MainActivity", "call onStart");
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MainActivity", "call onStop");
        unbindService(connection);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true;
        }
        else if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            MenuDialogFragment menuDialogFragment = new MenuDialogFragment();
            menuDialogFragment.show(getSupportFragmentManager(), "menu");
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean isKeepScreenOn() {
        return isKeepScreenOn;
    }

    public void setKeepScreenOn(boolean isKeepScreenOn) {
        this.isKeepScreenOn = isKeepScreenOn;
        if (isKeepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void showExitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("終了しますか？");
        builder.setPositiveButton("はい", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("MainActivity", "call onActivityResult | request=" + requestCode + ", result=" + resultCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_FRIEND_CACHE:
                {
                    long id = data.getLongExtra(SNPickerActivity.EXTRA_USER_ID, -1);
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, id);
                    intent.putExtra(ProfileActivity.EXTRA_USER, serviceBound?service.getPrimaryUser():null);
                    startActivity(intent);
                    break;
                }
            }
        }
    }

    private void addTab(TabInfo tabInfo) {
        TweetListFragment fragment = new TweetListFragment();
        Bundle b = new Bundle();
        String title;
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_HOME:
                title = "Home";
                break;
            case TabType.TABTYPE_MENTION:
                title = "Mentions";
                break;
            case TabType.TABTYPE_DM:
                title = "DM";
                break;
            default:
                title = "?Unknown Tab";
                break;
        }
        b.putString(TweetListFragment.EXTRA_TITLE, title);
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);

        pageList.add(fragment);
    }

    private void reloadTabs() {
        pageList.clear();

        ArrayList<TabInfo> tabs = service.getDatabase().getTabs();
        for (TabInfo info : tabs) {
            addTab(info);
        }

        TabPagerAdapter adapter = new TabPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);

        currentPage = (TweetListFragment) pageList.get(0);
        tvTabText.setText(pageList.get(0).getTitle());
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MainActivity.this.service = binder.getService();
            serviceBound = true;
            twitter = MainActivity.this.service.getTwitter();
            users = MainActivity.this.service.getUsers();
            if (users.size() < 1) {
                Intent intent = new Intent(MainActivity.this, OAuthActivity.class);
                intent.putExtra(OAuthActivity.EXTRA_REBOOT, true);
                startActivityForResult(intent, REQUEST_OAUTH);
                finish();
            }
            else if (pageList.size() < 1) {
                reloadTabs();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public TwitterService getTwitterService() {
        return service;
    }

    class TabPagerAdapter extends FragmentStatePagerAdapter {

        public TabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return (Fragment) pageList.get(i);
        }

        @Override
        public int getCount() {
            return pageList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageList.get(position).getTitle();
        }
    }
}
