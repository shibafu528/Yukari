package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.SearchDialogFragment;
import shibafu.yukari.fragment.SearchListFragment;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.fragment.TweetListFragmentFactory;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;

public class MainActivity extends ActionBarActivity implements TwitterServiceDelegate, SearchDialogFragment.SearchDialogCallback {

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_FRIEND_CACHE = 2;

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isKeepScreenOn = false;

    private boolean isTouchTweet = false;
    private float tweetGestureYStart = 0;
    private float tweetGestureY = 0;

    private TweetListFragment currentPage;
    private ArrayList<TabInfo> pageList = new ArrayList<TabInfo>();
    private TextView tvTabText;
    private ViewPager viewPager;
    private ImageButton ibClose;

    private View.OnTouchListener tweetGestureListener = new View.OnTouchListener() {
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
                        return true;
                    }
                    break;
            }
            if (v.getId() == R.id.tweetgesture) return true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
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
                TabInfo info;
                for (int i = 0; i < pageList.size(); ++i) {
                    info = pageList.get(i);
                    menu.add(Menu.NONE, i, Menu.NONE, info.getTitle());
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
        tvTabText.setOnTouchListener(tweetGestureListener);

        ImageButton ibSearch = (ImageButton) findViewById(R.id.ibSearch);
        ibSearch.setOnTouchListener(tweetGestureListener);
        ibSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
                popupMenu.inflate(R.menu.search);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_search_tweets:
                            {
                                SearchDialogFragment dialogFragment = new SearchDialogFragment();
                                dialogFragment.show(getSupportFragmentManager(), "search");
                                break;
                            }
                            case R.id.action_show_user:
                            {
                                final EditText tvInput = new EditText(MainActivity.this);
                                tvInput.setHint("@screen_name (@省略可)");
                                tvInput.setInputType(InputType.TYPE_CLASS_TEXT);

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

                                        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                        intent.setData(Uri.parse("http://twitter.com/" + sn));
                                        intent.putExtra(ProfileActivity.EXTRA_USER, serviceBound?service.getPrimaryUser():null);
                                        startActivity(intent);
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
        area.setOnTouchListener(tweetGestureListener);

        ImageButton ibMenu = (ImageButton) findViewById(R.id.ibMenu);
        ibMenu.setOnTouchListener(tweetGestureListener);
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

        ibClose = (ImageButton) findViewById(R.id.ibClose);
        ibClose.setOnTouchListener(tweetGestureListener);
        ibClose.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (currentPage.isCloseable()) {
                    int current = viewPager.getCurrentItem();
                    pageList.remove(current);
                    viewPager.setAdapter(new TabPagerAdapter(getSupportFragmentManager()));
                    viewPager.setCurrentItem(current - 1);
                    return true;
                }
                else return false;
            }
        });
        ibClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage.isCloseable()) {
                    Toast.makeText(MainActivity.this, "長押しでタブを閉じる", Toast.LENGTH_SHORT).show();
                }
            }
        });

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {

            }

            @Override
            public void onPageSelected(int i) {
                tvTabText.setText(pageList.get(i).getTitle());
                currentPage = (TweetListFragment) pageList.get(i).getAttachableList();
                if (currentPage.isCloseable()) {
                    ibClose.setVisibility(View.VISIBLE);
                }
                else {
                    ibClose.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        //スリープ防止設定
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        setKeepScreenOn(sp.getBoolean("pref_boot_screenon", false));

        //初回起動時の操作ガイド
        if (!sp.getBoolean("first_guide", false)) {
            final View guideView = getLayoutInflater().inflate(R.layout.overlay_guide, null);
            guideView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            addContentView(guideView,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            guideView.findViewById(R.id.bGuideClose).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.anim_fadeout);
                    guideView.startAnimation(animation);
                    guideView.setVisibility(View.GONE);
                    guideView.setClickable(false);
                    v.setFocusable(false);
                    v.setClickable(false);

                    sp.edit().putBoolean("first_guide", true).commit();
                }
            });
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("screen", isKeepScreenOn);
        outState.putSerializable("pagelist", pageList);
        getSupportFragmentManager().putFragment(outState, "current", currentPage);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isKeepScreenOn = savedInstanceState.getBoolean("screen");
        currentPage = (TweetListFragment) getSupportFragmentManager().getFragment(savedInstanceState, "current");
        pageList = (ArrayList<TabInfo>) savedInstanceState.getSerializable("pagelist");
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
        TweetListFragment fragment = TweetListFragmentFactory.create(tabInfo.getType());
        Bundle b = new Bundle();
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_SEARCH:
            case TabType.TABTYPE_TRACK:
                b.putString(SearchListFragment.EXTRA_SEARCH_QUERY, tabInfo.getSearchKeyword());
                break;
        }
        b.putString(TweetListFragment.EXTRA_TITLE, tabInfo.getTitle());
        b.putInt(TweetListFragment.EXTRA_MODE, tabInfo.getType());
        b.putSerializable(TweetListFragment.EXTRA_USER, tabInfo.getBindAccount());
        fragment.setArguments(b);
        tabInfo.setAttachableList(fragment);

        pageList.add(tabInfo);
    }

    private void initTabs(boolean reload) {
        int pageId = 0;
        if (reload) {
            pageList.clear();

            ArrayList<TabInfo> tabs = service.getDatabase().getTabs();
            for (TabInfo info : tabs) {
                addTab(info);
            }
        }
        else {
            for (int i = 0; i < pageList.size(); ++i) {
                if (pageList.get(i).getAttachableList() == currentPage) {
                    pageId = i;
                    break;
                }
            }
        }

        TabPagerAdapter adapter = new TabPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);

        currentPage = (TweetListFragment) pageList.get(pageId).getAttachableList();
        tvTabText.setText(pageList.get(pageId).getTitle());
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MainActivity.this.service = binder.getService();
            serviceBound = true;

            if (MainActivity.this.service.getUsers().isEmpty()) {
                Intent intent = new Intent(MainActivity.this, OAuthActivity.class);
                intent.putExtra(OAuthActivity.EXTRA_REBOOT, true);
                startActivityForResult(intent, REQUEST_OAUTH);
                finish();
            }
            else {
                initTabs(pageList.isEmpty());
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

    @Override
    public void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking) {
        TabInfo tabInfo = new TabInfo(
                TabType.TABTYPE_SEARCH, pageList.size(), service.getPrimaryUser(), searchQuery);
        addTab(tabInfo);
        viewPager.getAdapter().notifyDataSetChanged();
        viewPager.setCurrentItem(tabInfo.getOrder());
    }

    class TabPagerAdapter extends FragmentStatePagerAdapter {

        public TabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return (Fragment) pageList.get(i).getAttachableList();
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
