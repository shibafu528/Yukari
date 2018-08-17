package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.LongSparseArray;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTouch;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TriangleView;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.Provider;
import shibafu.yukari.entity.Status;
import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.FilterCompilerException;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.filter.compiler.TokenizeException;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.QuickPostFragment;
import shibafu.yukari.fragment.SearchDialogFragment;
import shibafu.yukari.fragment.tabcontent.DefaultTweetListFragment;
import shibafu.yukari.fragment.tabcontent.SearchListFragment;
import shibafu.yukari.fragment.tabcontent.StreamToggleable;
import shibafu.yukari.fragment.tabcontent.TimelineTab;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterStream;
import shibafu.yukari.twitter.streaming.FilterStream;
import shibafu.yukari.util.ReferenceHolder;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterResponse;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends ActionBarYukariBase implements SearchDialogFragment.SearchDialogCallback, QuickPostFragment.OnCloseQuickPostListener {

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT = 4;
    private static final int REQUEST_QUERY = 8;

    public static final String EXTRA_SEARCH_WORD = "searchWord";
    public static final String EXTRA_SHOW_TAB = "showTab";

    private SharedPreferences sharedPreferences;

    private boolean keepScreenOn = false;

    private boolean immersive = false;
    private View decorView;

    private boolean isTouchTweet = false;
    private float tweetGestureYStart = 0;
    private float tweetGestureY = 0;

    private int currentPageIndex = -1;
    private Fragment currentPage;
    private ArrayList<TabInfo> pageList = new ArrayList<>();
    private Map<String, List<Status>> pageStatuses = new ArrayMap<>();
    private Map<Long, ArrayList<? extends TwitterResponse>> pageElements = new ArrayMap<>();
    private Map<Long, LongSparseArray<Long>> lastStatusIdsArrays = new ArrayMap<>();
    private Map<Long, ReferenceHolder<twitter4j.Query>> searchQueries = new ArrayMap<>();
    @BindView(R.id.tvMainTab)     TextView tvTabText;
    @BindView(R.id.pager)         ViewPager viewPager;
    @BindView(R.id.ibClose)       ImageButton ibClose;
    @BindView(R.id.ibStream)      ImageButton ibStream;
    @BindView(R.id.llTweetGuide)  LinearLayout llTweetGuide;
    @BindView(R.id.streamState)   TriangleView tvStreamState;

    private LongSparseArray<WeakReference<Fragment>> tabRegistry = new LongSparseArray<>();

    //QuickPost関連
    private boolean enableQuickPost = true;
    @BindView(R.id.llQuickTweet)  LinearLayout llQuickTweet;

    //投稿ボタン関連
    @BindView(R.id.tweetbutton_frame) FrameLayout flTweet;

    private TabPagerAdapter tabPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(getApplicationContext(), getString(R.string.error_storage_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        findViews();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && new File(getExternalFilesDir(null), "wallpaper").exists()) {
            Drawable wallpaper = Drawable.createFromPath(new File(getExternalFilesDir(null), "wallpaper").getAbsolutePath());
            wallpaper.setAlpha(72);
            viewPager.setBackground(wallpaper);
        }

        //スリープ防止設定
        setKeepScreenOn(sharedPreferences.getBoolean("pref_boot_screenon", false));

        //表示域拡張設定
        setImmersive(sharedPreferences.getBoolean("pref_boot_immersive", false));

        //サービスの常駐
        if (sharedPreferences.getBoolean("pref_enable_service", false)) {
            startService(new Intent(getApplicationContext(), TwitterService.class));
        }
    }

    private void findViews() {
        decorView = getWindow().getDecorView();
        ButterKnife.bind(this);

        View flStreamState = findViewById(R.id.flStreamState);
        flStreamState.setOnClickListener(v -> {
            if (isImmersive()) {
                setImmersive(false, true);
                Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(() -> setImmersive(true, true), 3000);
            }
        });
        flStreamState.setOnLongClickListener(view -> {
            if (enableQuickPost) {
                llQuickTweet.setVisibility(View.VISIBLE);

                QuickPostFragment quickPostFragment = (QuickPostFragment) getSupportFragmentManager().findFragmentById(R.id.flgQuickPost);
                if (quickPostFragment != null) {
                    if (currentPage instanceof SearchListFragment) {
                        quickPostFragment.setDefaultText(" " + ((SearchListFragment) currentPage).getStreamFilter());
                    } else {
                        quickPostFragment.setDefaultText("");
                    }
                }
                return true;
            } else return false;
        });

        tvTabText.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
            Menu menu = popupMenu.getMenu();
            TabInfo info;
            for (int i = 0; i < pageList.size(); ++i) {
                info = pageList.get(i);
                menu.add(Menu.NONE, i, Menu.NONE, info.getTitle());
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                viewPager.setCurrentItem(menuItem.getItemId(), true);
                return true;
            });
            popupMenu.show();
        });
        tvTabText.setOnLongClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
            Menu menu = popupMenu.getMenu();
            menu.add(Menu.NONE, 0, 0, "⇧ TLの一番上へ");
            menu.add(Menu.NONE, 2, 1, "◇ タブの編集");
            menu.add(Menu.NONE, 1, 9, "⇩ TLの一番下へ");
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case 0:
                        if (currentPage instanceof TimelineTab) {
                            ((TimelineTab) currentPage).scrollToTop();
                        }
                        return true;
                    case 1:
                        if (currentPage instanceof TimelineTab) {
                            ((TimelineTab) currentPage).scrollToBottom();
                        }
                        return true;
                    case 2:
                        startActivity(new Intent(getApplicationContext(), TabEditActivity.class));
                        return true;
                }
                return false;
            });
            popupMenu.show();
            return true;
        });

        ImageButton ibSearch = (ImageButton) findViewById(R.id.ibSearch);
        ibSearch.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
            popupMenu.inflate(R.menu.search);
            if (currentPage instanceof SearchListFragment) {
                popupMenu.getMenu().findItem(R.id.action_save_search).setVisible(true);
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.action_save_search: {
                        Intent intent = new Intent(getApplicationContext(), AccountChooserActivity.class);
                        startActivityForResult(intent, REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT);
                        break;
                    }
                    case R.id.action_search_tweets: {
                        SearchDialogFragment dialogFragment = new SearchDialogFragment();
                        dialogFragment.show(getSupportFragmentManager(), "search");
                        break;
                    }
                    case R.id.action_search_users:
                        startActivity(new Intent(getApplicationContext(), UserSearchActivity.class));
                        break;
                    case R.id.action_query:
                        startActivityForResult(new Intent(getApplicationContext(), QueryEditorActivity.class), REQUEST_QUERY);
                        break;
                }
                return false;
            });

            popupMenu.show();
        });

        ImageButton ibMenu = (ImageButton) findViewById(R.id.ibMenu);
        ibMenu.setOnClickListener(v -> {
            MenuDialogFragment menuDialogFragment = new MenuDialogFragment();
            menuDialogFragment.show(getSupportFragmentManager(), "menu");
        });
        if (!sharedPreferences.getBoolean("pref_show_menubutton", false) && ViewConfiguration.get(this).hasPermanentMenuKey()) {
            ibMenu.setVisibility(View.GONE);
        }

        ibClose.setOnLongClickListener(view -> {
            if (currentPage instanceof TimelineTab && ((TimelineTab) currentPage).isCloseable()) {
                int current = viewPager.getCurrentItem();
                TabInfo tabInfo = pageList.get(current);
                Fragment tabFragment = null;
                WeakReference<Fragment> reference = tabRegistry.get(pageList.get(current).getId());
                if (reference != null) {
                    tabFragment = reference.get();
                }
                if (tabFragment instanceof StreamToggleable &&
                        ((StreamToggleable) tabFragment).isStreaming()) {
                    ((StreamToggleable) tabFragment).setStreaming(false);
                }

                pageList.remove(current);
                onTabChanged(current - 1);
                tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
                viewPager.setAdapter(tabPagerAdapter);
                viewPager.setCurrentItem(current - 1);
                return true;
            } else return false;
        });
        ibClose.setOnClickListener(view -> {
            if (currentPage instanceof TimelineTab && ((TimelineTab) currentPage).isCloseable()) {
                Toast.makeText(getApplicationContext(), "長押しでタブを閉じる", Toast.LENGTH_SHORT).show();
            }
        });

        ibStream.setOnClickListener(view -> {
            if (currentPage instanceof StreamToggleable) {
                boolean isStreaming = !((StreamToggleable) currentPage).isStreaming();
                ((StreamToggleable) currentPage).setStreaming(isStreaming);
                if (isStreaming) {
                    ibStream.setImageResource(R.drawable.ic_play_d);
                }
                else {
                    ibStream.setImageResource(R.drawable.ic_pause_d);
                }
            }
        });

        tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(tabPagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int i) {
                onTabChanged(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        ImageView ivTweet = (ImageView) findViewById(R.id.ivTweet);
        ivTweet.setOnClickListener(v -> startTweetActivity());
    }

    @OnTouch({R.id.tweetgesture, R.id.llMainFooterRight, R.id.flStreamState, R.id.tvMainTab,
            R.id.ibSearch, R.id.ibMenu, R.id.ibClose, R.id.ibStream, R.id.ivTweet,
            R.id.ibCloseTweet, R.id.ibAccount, R.id.etTweetInput, R.id.ibTweet})
    boolean onTouchFooter(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                tweetGestureYStart = event.getY();
            case MotionEvent.ACTION_MOVE:
                tweetGestureY = event.getY();
                isTouchTweet = true;
                break;
            case MotionEvent.ACTION_UP:
                if (isTouchTweet && Math.abs(tweetGestureYStart - tweetGestureY) > 80) {
                    startTweetActivity();
                    return true;
                }
                break;
        }
        return v.getId() == R.id.tweetgesture;
    }

    @OnClick(R.id.llMainFooterRight)
    void onClickFooterSpace() {
        if (currentPage instanceof TimelineTab && sharedPreferences.getBoolean("pref_quick_scroll_to_top", false)) {
            ((TimelineTab) currentPage).scrollToTop();
        }
    }

    @OnLongClick(R.id.llMainFooterRight)
    boolean onLongClickFooterSpace() {
        TabInfo tabInfo = new TabInfo(TabType.TABTYPE_DON_PUBLIC, pageList.size(), getTwitterService().getPrimaryUser());
        addTab(tabInfo);
        viewPager.getAdapter().notifyDataSetChanged();
        viewPager.setCurrentItem(tabInfo.getOrder());
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_SEARCH_WORD)) {
            onSearchQuery(intent.getStringExtra(EXTRA_SEARCH_WORD), false, false);
        }
        else if (intent.getData() != null && intent.getData().getHost().equals("shibafu.yukari.link")) {
            String hash = "#" + intent.getData().getLastPathSegment();
            onSearchQuery(hash, false, false);
        }
        else if (intent.hasExtra(EXTRA_SHOW_TAB)) {
            int tabType = intent.getIntExtra(EXTRA_SHOW_TAB, -1);
            if (tabType > -1) {
                for (TabInfo info : pageList) {
                    if (info.getType() == tabType) {
                        viewPager.setCurrentItem(pageList.indexOf(info));
                        return;
                    }
                }
                // If not exist...
                if (tabType == TabType.TABTYPE_BOOKMARK) {
                    TabInfo tabInfo = new TabInfo(tabType, pageList.size(), getTwitterService().getPrimaryUser());
                    addTab(tabInfo);
                    viewPager.getAdapter().notifyDataSetChanged();
                    viewPager.setCurrentItem(tabInfo.getOrder());
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //スリープ有効の設定
        setKeepScreenOn(keepScreenOn);
        //表示域拡張の設定
        setImmersive(immersive);
        //ツイート操作ガイド
        llTweetGuide.setVisibility(sharedPreferences.getBoolean("first_guide", true)? View.VISIBLE : View.GONE);
        //投稿ボタン
        flTweet.setVisibility(sharedPreferences.getBoolean("pref_show_tweetbutton", false)? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("screen", keepScreenOn);
        outState.putBoolean("immersive", immersive);
        if (currentPage != null && currentPage.isAdded()) {
            getSupportFragmentManager().putFragment(outState, "current", currentPage);
        }
        outState.putInt("currentPageIndex", currentPageIndex);
        outState.putSerializable("tabinfo", pageList);

        Log.d("MainActivity", "call onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d("MainActivity", "call onRestoreInstanceState");

        keepScreenOn = savedInstanceState.getBoolean("screen");
        immersive = savedInstanceState.getBoolean("immersive");
        currentPage = getSupportFragmentManager().getFragment(savedInstanceState, "current");
        currentPageIndex = savedInstanceState.getInt("currentPageIndex");
        pageList = (ArrayList<TabInfo>) savedInstanceState.getSerializable("tabinfo");
        if (pageList == null) {
            pageList = new ArrayList<>();
        }
        tabPagerAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        currentPage = null;
        currentPageIndex = -1;
        tabPagerAdapter = null;
        viewPager = null;
        sharedPreferences = null;

        for (ArrayList<? extends TwitterResponse> list : pageElements.values()) {
            list.clear();
        }
        pageElements.clear();
        for (LongSparseArray<Long> array : lastStatusIdsArrays.values()) {
            array.clear();
        }
        lastStatusIdsArrays.clear();
        searchQueries.clear();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                stopService(new Intent(getApplicationContext(), TwitterService.class));
                finish();
            }
            else if (event.getAction() == KeyEvent.ACTION_UP && !event.isLongPress()) {
                if (llQuickTweet.getVisibility() == View.VISIBLE) {
                    llQuickTweet.setVisibility(View.GONE);
                }
                else {
                    showExitDialog();
                }
            }
            return true;
        }
        else if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            MenuDialogFragment menuDialogFragment = new MenuDialogFragment();
            menuDialogFragment.show(getSupportFragmentManager(), "menu");
            return true;
        }
        else if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_1:
                    startTweetActivity();
                    return true;
                case KeyEvent.KEYCODE_2:
                    if (currentPage instanceof TimelineTab) {
                        ((TimelineTab) currentPage).scrollToTop();
                    }
                    return true;
                case KeyEvent.KEYCODE_5:
                    if (currentPage instanceof TimelineTab) {
                        ((TimelineTab) currentPage).scrollToOldestUnread();
                    }
                    return true;
                case KeyEvent.KEYCODE_8:
                    if (currentPage instanceof TimelineTab) {
                        ((TimelineTab) currentPage).scrollToBottom();
                    }
                    return true;
                case KeyEvent.KEYCODE_STAR:
                    if (event.isLongPress()) {
                        ibClose.performLongClick();
                    } else {
                        ibClose.performClick();
                    }
                    return true;
                case KeyEvent.KEYCODE_0:
                    (findViewById(R.id.ibSearch)).performClick();
                    return true;
                case KeyEvent.KEYCODE_F3:
                    if (currentPage instanceof TimelineTab) {
                        ((TimelineTab) currentPage).scrollToPrevPage();
                    }
                    return true;
                case KeyEvent.KEYCODE_F4:
                    if (currentPage instanceof TimelineTab) {
                        ((TimelineTab) currentPage).scrollToNextPage();
                    }
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean isKeepScreenOn() {
        return keepScreenOn;
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        this.keepScreenOn = keepScreenOn;
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public boolean isImmersive() {
        return immersive;
    }

    @Override
    public void setImmersive(boolean immersive) {
        setImmersive(immersive, false);
    }

    public void setImmersive(boolean immersive, boolean transientState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!transientState) {
                this.immersive = immersive;
            }
            if (immersive) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                decorView.setSystemUiVisibility(0);
            }
        } else {
            this.immersive = false;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            setImmersive(isImmersive());
        }
    }

    public void showExitDialog() {
        if (sharedPreferences.getBoolean("pref_dialog_quit", true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("終了しますか？");
            builder.setPositiveButton("はい", (dialog, which) -> {
                dialog.dismiss();
                stopService(new Intent(getApplicationContext(), TwitterService.class));
                finish();
            });
            builder.setNegativeButton("いいえ", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.show();
        } else {
            stopService(new Intent(getApplicationContext(), TwitterService.class));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("MainActivity", "call onActivityResult | request=" + requestCode + ", result=" + resultCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT:
                {
                    AuthUserRecord selectedAccount = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    class Args {
                        public AuthUserRecord account;
                        public String query;
                        public Args(AuthUserRecord account, String query) {
                            this.account = account;
                            this.query = query;
                        }
                    }
                    new TwitterAsyncTask<Args>(getApplicationContext()) {
                        @Override
                        protected TwitterException doInBackground(Args... params) {
                            try {
                                Twitter twitter = getTwitterService().getTwitterOrThrow(params[0].account);
                                twitter.createSavedSearch(params[0].query);
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                return e;
                            }
                            return null;
                        }

                        private void showToast(String text) {
                            Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        protected void onPreExecute() {
                            showToast("検索を保存しています...");
                        }

                        @Override
                        protected void onPostExecute(TwitterException e) {
                            if (e == null) {
                                showToast("検索を保存しました");
                            }
                            else {
                                showToast(String.format("検索の保存に失敗:%d\n%s", e.getErrorCode(), e.getErrorMessage()));
                            }
                        }
                    }.execute(new Args(selectedAccount, pageList.get(viewPager.getCurrentItem()).getSearchKeyword()));
                    break;
                }
                case REQUEST_QUERY: {
                    try {
                        long time = System.currentTimeMillis();

                        List<AuthUserRecord> users = getTwitterService().getUsers();
                        String rawQuery = data.getStringExtra(Intent.EXTRA_TEXT);
                        FilterQuery query = QueryCompiler.compile(users, rawQuery);
                        TabInfo tabInfo = new TabInfo(TabType.TABTYPE_FILTER, pageList.size(), null, rawQuery);
                        ArrayList<TwitterResponse> elements = new ArrayList<>(pageElements.get(pageList.get(viewPager.getCurrentItem()).getId()));
                        for (Iterator<TwitterResponse> iterator = elements.iterator(); iterator.hasNext(); ) {
                            TwitterResponse element = iterator.next();
                            if (!query.evaluate(element, users, new HashMap<>())) {
                                iterator.remove();
                            }
                        }
                        pageElements.put(tabInfo.getId(), elements);
                        addTab(tabInfo);
                        viewPager.getAdapter().notifyDataSetChanged();
                        viewPager.setCurrentItem(tabInfo.getOrder());

                        time = System.currentTimeMillis() - time;
                        Toast.makeText(getApplicationContext(), String.format("クエリ実行完了\n%s\n処理時間: %d ms\n抽出件数: %d 件", rawQuery, time, elements.size()), Toast.LENGTH_LONG).show();
                    } catch (FilterCompilerException | TokenizeException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
                    }
                    break;
                }
            }
        }
    }

    private void startTweetActivity() {
        Intent intent = new Intent(getApplicationContext(), TweetActivity.class);
        if (sharedPreferences.getBoolean("pref_use_binded_user", false)
                && currentPage instanceof DefaultTweetListFragment) {
            DefaultTweetListFragment current = (DefaultTweetListFragment) this.currentPage;
            if (current.getBoundUsers().size() == 1) {
                switch (current.getMode()) {
                    case TabType.TABTYPE_HOME:
                    case TabType.TABTYPE_MENTION:
                    case TabType.TABTYPE_DM:
                    case TabType.TABTYPE_LIST:
                        intent.putExtra(TweetActivity.EXTRA_USER, current.getCurrentUser());
                        break;
                }
            }
        }
        startActivity(intent);
    }

    private void addTab(TabInfo tabInfo) {
        pageList.add(tabInfo);
    }

    private void initTabs(boolean reload) {
        int pageId = currentPageIndex;
        if (reload) {
            pageList.clear();

            ArrayList<TabInfo> tabs = getTwitterService().getDatabase().getTabs();
            for (int i = 0; i < tabs.size(); i++) {
                addTab(tabs.get(i));
                if (tabs.get(i).isStartup()) {
                    pageId = i;
                }
            }
        }
        if (pageId < 0) {
            pageId = 0;
        }

        tabPagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(pageId);

        if (!pageList.isEmpty()) {
            WeakReference<Fragment> reference = tabRegistry.get(pageList.get(pageId).getId());
            if (reference != null) {
                currentPage = reference.get();
            }
            tvTabText.setText(pageList.get(pageId).getTitle());
            currentPage = tabPagerAdapter.instantiateItem(pageId);
            currentPageIndex = pageId;
        }
        else {
            tvTabText.setText("!EMPTY!");
        }
    }

    private void onTabChanged(int position) {
        TabInfo tabInfo = pageList.get(position);

        tvTabText.setText(tabInfo.getTitle());
        currentPage = tabPagerAdapter.instantiateItem(position);
        currentPageIndex = position;

        if (currentPage != null) {
            if (currentPage instanceof TimelineTab && ((TimelineTab) currentPage).isCloseable()) {
                ibClose.setVisibility(View.VISIBLE);
            } else {
                ibClose.setVisibility(View.INVISIBLE);
            }
            if (currentPage instanceof StreamToggleable) {
                ibStream.setVisibility(View.VISIBLE);
                if (((StreamToggleable) currentPage).isStreaming()) {
                    ibStream.setImageResource(R.drawable.ic_play_d);
                } else {
                    ibStream.setImageResource(R.drawable.ic_pause_d);
                }
            } else {
                ibStream.setVisibility(View.INVISIBLE);
            }
        } else {
            Toast.makeText(getApplicationContext(), "Tab Change Error", Toast.LENGTH_LONG).show();
            ibClose.setVisibility(View.INVISIBLE);
            ibStream.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking) {
        //オプション追記
        if (!isSavedSearch) {
            if (sharedPreferences.getBoolean("pref_search_ja", false) && !searchQuery.contains("lang:ja")) {
                searchQuery += " lang:ja";
            }
            if (sharedPreferences.getBoolean("pref_search_minus_rt", false) && !searchQuery.contains(" -RT")) {
                searchQuery += " -RT";
            }
        }

        boolean exist = false;
        int existId = -1;
        for (int i = 0; i < pageList.size(); i++) {
            TabInfo tabInfo = pageList.get(i);
            //既に同じようなタブがある場合は作らない
            if (tabInfo.getType() == TabType.TABTYPE_SEARCH && tabInfo.getSearchKeyword().equals(searchQuery)) {
                exist = true;
                existId = i;
                break;
            }
        }

        if (!exist) {
            TabInfo tabInfo = new TabInfo(
                    TabType.TABTYPE_SEARCH, pageList.size(), getTwitterService().getPrimaryUser(), searchQuery);
            addTab(tabInfo);
            viewPager.getAdapter().notifyDataSetChanged();
            viewPager.setCurrentItem(tabInfo.getOrder());
        }
        else {
            viewPager.setCurrentItem(existId);
        }
    }

    @Override
    public void onCloseQuickPost() {
        llQuickTweet.setVisibility(View.GONE);
    }

    @NonNull
    public List<shibafu.yukari.entity.Status> getStatusesList(String id) {
        if (!pageStatuses.containsKey(id)) {
            pageStatuses.put(id, new ArrayList<>());
        }
        return pageStatuses.get(id);
    }

    public <T extends TwitterResponse> ArrayList<T> getElementsList(long id) {
        if (!pageElements.containsKey(id)) {
            pageElements.put(id, new ArrayList<T>());
        }
        return (ArrayList<T>) pageElements.get(id);
    }

    public LongSparseArray<Long> getLastStatusIdsArray(long id) {
        if (!lastStatusIdsArrays.containsKey(id)) {
            lastStatusIdsArrays.put(id, new LongSparseArray<>());
        }
        return lastStatusIdsArrays.get(id);
    }

    public ReferenceHolder<twitter4j.Query> getSearchQuery(long id) {
        if (!searchQueries.containsKey(id)) {
            searchQueries.put(id, new ReferenceHolder<>());
        }
        return searchQueries.get(id);
    }

    public void registTwitterFragment(long id, Fragment fragment) {
        Log.d("MainActivity", "regist tab: " + id);
        tabRegistry.put(id, new WeakReference<>(fragment));
    }

    public void unregistTwitterFragment(long id) {
        Log.d("MainActivity", "unregist tab: " + id);
        tabRegistry.remove(id);
    }

    @Override
    public void onServiceConnected() {
        if (getTwitterService().getUsers().isEmpty()) {
            Intent intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            startActivityForResult(intent, REQUEST_OAUTH);
            finish();
        }
        else {
            initTabs(pageList.isEmpty());

            QuickPostFragment quickPostFragment = (QuickPostFragment) getSupportFragmentManager().findFragmentById(R.id.flgQuickPost);
            if (quickPostFragment != null && quickPostFragment.getSelectedAccount() == null) {
                AuthUserRecord primary = getTwitterService().getPrimaryUser();
                if (primary == null) {
                    Toast.makeText(getApplicationContext(), "プライマリアカウントが取得できません\nクイック投稿は無効化されます", Toast.LENGTH_SHORT).show();
                    enableQuickPost = false;
                } else {
                    quickPostFragment.setSelectedAccount(primary);
                    enableQuickPost = true;
                }
            }

            //UserStreamを開始する
            getTwitterService().startStreamChannels();

            onNewIntent(getIntent());
        }
    }

    @Override
    public void onServiceDisconnected() {}

    class TabPagerAdapter extends FragmentStatePagerAdapter {

        public TabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            TabInfo tabInfo = pageList.get(i);

            Fragment fragment = TweetListFragmentFactory.newInstanceWithFilter(tabInfo);
            switch (tabInfo.getType()) {
                case TabType.TABTYPE_TRACK:
                    //現状ここに行き着くことってそんなに無い気がする
                    if (tabInfo.getBindAccount().Provider.getApiType() == Provider.API_TWITTER) {
                        TwitterStream stream = (TwitterStream) getTwitterService().getProviderStream(tabInfo.getBindAccount());
                        if (stream != null) {
                            stream.startFilterStream(new FilterStream.ParsedQuery(tabInfo.getSearchKeyword()).getValidQuery(),
                                    tabInfo.getBindAccount());
                        }
                    }
                    break;
            }

            return fragment;
        }

        @Override
        public int getCount() {
            return pageList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageList.get(position).getTitle();
        }

        public Fragment instantiateItem(int position) {
            return (Fragment) super.instantiateItem(viewPager, position);
        }

        public Fragment instantiateItem() {
            return (Fragment) super.instantiateItem(viewPager, viewPager.getCurrentItem());
        }
    }
}
