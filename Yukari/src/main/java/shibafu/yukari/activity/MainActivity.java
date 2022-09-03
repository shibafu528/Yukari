package shibafu.yukari.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.collection.ArrayMap;
import androidx.collection.LongSparseArray;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Provider;
import shibafu.yukari.databinding.ActivityMainBinding;
import shibafu.yukari.entity.Status;
import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.FilterCompilerException;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.filter.compiler.TokenizeException;
import shibafu.yukari.filter.source.DynamicChannelController;
import shibafu.yukari.filter.source.FilterSource;
import shibafu.yukari.filter.source.Search;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.QuickPostFragment;
import shibafu.yukari.fragment.SearchDialogFragment;
import shibafu.yukari.fragment.tabcontent.QueryableTab;
import shibafu.yukari.fragment.tabcontent.TimelineFragment;
import shibafu.yukari.fragment.tabcontent.TimelineTab;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.TwitterStream;
import shibafu.yukari.twitter.streaming.FilterStream;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public class MainActivity extends ActionBarYukariBase implements SearchDialogFragment.SearchDialogCallback, QuickPostFragment.OnCloseQuickPostListener {

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT = 4;
    private static final int REQUEST_QUERY = 8;

    private static final int PERMISSION_REQUEST_POST_NOTIFICATIONS = 1001;

    public static final String EXTRA_SEARCH_WORD = "searchWord";
    public static final String EXTRA_SHOW_TAB = "showTab";

    private ActivityMainBinding binding;
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

    private LongSparseArray<WeakReference<Fragment>> tabRegistry = new LongSparseArray<>();

    //QuickPost関連
    private boolean enableQuickPost = true;

    private TabPagerAdapter tabPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().hide();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(getApplicationContext(), getString(R.string.error_storage_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        findViews();

        if (new File(getExternalFilesDir(null), "wallpaper").exists()) {
            Drawable wallpaper = Drawable.createFromPath(new File(getExternalFilesDir(null), "wallpaper").getAbsolutePath());
            wallpaper.setAlpha(72);
            binding.pager.setBackground(wallpaper);
        }

        //スリープ防止設定
        setKeepScreenOn(sharedPreferences.getBoolean("pref_boot_screenon", false));

        //表示域拡張設定
        setImmersive(sharedPreferences.getBoolean("pref_boot_immersive", false));

        //サービスの常駐
        if (sharedPreferences.getBoolean("pref_enable_service", false)) {
            ContextCompat.startForegroundService(this, new Intent(getApplicationContext(), TwitterService.class));
        }
    }

    private void findViews() {
        decorView = getWindow().getDecorView();

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
                showQuickPost();
                return true;
            } else return false;
        });

        binding.tvMainTab.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
            Menu menu = popupMenu.getMenu();
            TabInfo info;
            for (int i = 0; i < pageList.size(); ++i) {
                info = pageList.get(i);
                menu.add(Menu.NONE, i, Menu.NONE, info.getTitle());
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                binding.pager.setCurrentItem(menuItem.getItemId(), true);
                return true;
            });
            popupMenu.show();
        });
        binding.tvMainTab.setOnLongClickListener(v -> {
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
            TwitterService service = getTwitterService();
            if (service != null) {
                // Mastodonアカウントを持っている場合、紛らわしいメニュー項目にprefixを付ける
                boolean hasMastodonAccount = service.getUsers().stream().anyMatch(userRecord -> userRecord.Provider.getApiType() == Provider.API_MASTODON);
                if (hasMastodonAccount) {
                    MenuItem searchUsers = popupMenu.getMenu().findItem(R.id.action_search_users);
                    searchUsers.setTitle("Twitter " + searchUsers.getTitle());
                }
            }
            if (currentPage instanceof TimelineFragment) {
                FilterQuery query = ((TimelineFragment) currentPage).getQuery();
                for (FilterSource source : query.getSources()) {
                    if (source instanceof Search) {
                        popupMenu.getMenu().findItem(R.id.action_save_search).setVisible(true);
                        break;
                    }
                }
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.action_save_search: {
                        Intent intent = new Intent(getApplicationContext(), AccountChooserActivity.class);
                        intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_TWITTER);
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
                        if (currentPage instanceof QueryableTab) {
                            startActivityForResult(new Intent(getApplicationContext(), QueryEditorActivity.class), REQUEST_QUERY);
                        } else {
                            Toast.makeText(getApplicationContext(), "このタブではクエリを実行できません。", Toast.LENGTH_SHORT).show();
                        }
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

        binding.ibClose.setOnLongClickListener(view -> {
            int current = binding.pager.getCurrentItem();
            TabInfo tabInfo = pageList.get(current);
            if (tabInfo.isCloseable()) {
                Fragment tabFragment = null;
                WeakReference<Fragment> reference = tabRegistry.get(tabInfo.getId());
                if (reference != null) {
                    tabFragment = reference.get();
                }

                if (tabFragment instanceof TimelineFragment) {
                    TwitterService service = getTwitterService();
                    FilterQuery query = ((TimelineFragment) tabFragment).getQuery();
                    if (service != null && query.isConnectedAnyDynamicChannel(service)) {
                        query.disconnectAllDynamicChannel(service);
                    }
                }

                pageList.remove(current);
                onTabChanged(current - 1);
                tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
                binding.pager.setAdapter(tabPagerAdapter);
                binding.pager.setCurrentItem(current - 1);
                return true;
            } else return false;
        });
        binding.ibClose.setOnClickListener(view -> {
            if (pageList.get(binding.pager.getCurrentItem()).isCloseable()) {
                Toast.makeText(getApplicationContext(), "長押しでタブを閉じる", Toast.LENGTH_SHORT).show();
            }
        });

        binding.ibStream.setOnClickListener(view -> {
            if (currentPage instanceof TimelineFragment) {
                TwitterService service = getTwitterService();
                if (service == null) {
                    return;
                }

                FilterQuery query = ((TimelineFragment) currentPage).getQuery();
                boolean isStreaming = query.isConnectedAnyDynamicChannel(service);
                if (isStreaming) {
                    query.disconnectAllDynamicChannel(service);
                    binding.ibStream.setImageResource(R.drawable.ic_pause_d);
                } else {
                    query.connectAllDynamicChannel(service);
                    binding.ibStream.setImageResource(R.drawable.ic_play_d);
                }
            }
        });

        tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
        binding.pager.setAdapter(tabPagerAdapter);
        binding.pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
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

        binding.llMainFooterRight.setOnClickListener(this::onClickFooterSpace);

        View[] onTouchFooterTargets = {
                binding.tweetgesture, binding.llMainFooterRight, binding.flStreamState, binding.tvMainTab,
                binding.ibSearch, binding.ibMenu, binding.ibClose, binding.ibStream, binding.ivTweet,
                findViewById(R.id.ibCloseTweet), findViewById(R.id.ibAccount), findViewById(R.id.etTweetInput), findViewById(R.id.ibTweet)
        };
        for (View v : onTouchFooterTargets) {
            v.setOnTouchListener(this::onTouchFooter);
        }
    }

    boolean onTouchFooter(View v, MotionEvent event) {
        if (sharedPreferences.getBoolean("pref_show_tweetbutton", false) && sharedPreferences.getBoolean("pref_disable_tweet_gesture", false)) {
            return v.getId() == R.id.tweetgesture;
        }

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

    void onClickFooterSpace(View v) {
        if (currentPage instanceof TimelineTab && sharedPreferences.getBoolean("pref_quick_scroll_to_top", false)) {
            ((TimelineTab) currentPage).scrollToTop();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_SEARCH_WORD)) {
            onSearchQuery(intent.getStringExtra(EXTRA_SEARCH_WORD), false, false);
        }
        else if (intent.hasExtra(EXTRA_SHOW_TAB)) {
            int tabType = intent.getIntExtra(EXTRA_SHOW_TAB, -1);
            if (tabType > -1) {
                for (TabInfo info : pageList) {
                    if (info.getType() == tabType) {
                        binding.pager.setCurrentItem(pageList.indexOf(info));
                        return;
                    }
                }
                // If not exist...
                if (tabType == TabType.TABTYPE_BOOKMARK) {
                    TabInfo tabInfo = new TabInfo(tabType, pageList.size(), getTwitterService().getPrimaryUser());
                    tabInfo.setCloseable(true);
                    addTab(tabInfo);
                    binding.pager.getAdapter().notifyDataSetChanged();
                    binding.pager.setCurrentItem(tabInfo.getOrder());
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
        binding.llTweetGuide.setVisibility(sharedPreferences.getBoolean("first_guide", true)? View.VISIBLE : View.GONE);
        //投稿ボタン
        binding.tweetbuttonFrame.setVisibility(sharedPreferences.getBoolean("pref_show_tweetbutton", false)? View.VISIBLE : View.GONE);
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
        sharedPreferences = null;

        pageStatuses.clear();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                stopService(new Intent(getApplicationContext(), TwitterService.class));
                finish();
            }
            else if (event.getAction() == KeyEvent.ACTION_UP && !event.isLongPress()) {
                if (binding.llQuickTweet.getVisibility() == View.VISIBLE) {
                    binding.llQuickTweet.setVisibility(View.GONE);
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
                        binding.ibClose.performLongClick();
                    } else {
                        binding.ibClose.performClick();
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

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!binding.llQuickTweet.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_M:
                    new MenuDialogFragment().show(getSupportFragmentManager(), "menu");
                    return true;
                case KeyEvent.KEYCODE_T:
                    startTweetActivity();
                    return true;
                case KeyEvent.KEYCODE_S:
                    (findViewById(R.id.ibSearch)).performClick();
                    return true;
            }
        }
        return super.onKeyUp(keyCode, event);
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
        if (!transientState) {
            this.immersive = immersive;
        }
        if (immersive) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            decorView.setSystemUiVisibility(0);
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
                    }.execute(new Args(selectedAccount, pageList.get(binding.pager.getCurrentItem()).getSearchKeyword()));
                    break;
                }
                case REQUEST_QUERY: {
                    try {
                        if (!(currentPage instanceof QueryableTab)) {
                            Toast.makeText(getApplicationContext(), "クエリの実行に失敗しました。", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        long time = System.currentTimeMillis();

                        List<AuthUserRecord> users = getTwitterService().getUsers();
                        String rawQuery = data.getStringExtra(Intent.EXTRA_TEXT);
                        FilterQuery query = QueryCompiler.compile(users, rawQuery);
                        TabInfo tabInfo = new TabInfo(TabType.TABTYPE_FILTER, pageList.size(), null, rawQuery);
                        Collection<Status> queryableElements = ((QueryableTab) currentPage).getQueryableElements();
                        ArrayList<Status> resultElements = new ArrayList<>();
                        for (Status element : queryableElements) {
                            if (query.evaluate(element, users, new HashMap<>())) {
                                resultElements.add(element);
                            }
                        }
                        pageStatuses.put(String.valueOf(tabInfo.getId()), resultElements);
                        addTab(tabInfo);
                        binding.pager.getAdapter().notifyDataSetChanged();
                        binding.pager.setCurrentItem(tabInfo.getOrder());

                        time = System.currentTimeMillis() - time;
                        Toast.makeText(getApplicationContext(), String.format(Locale.US, "クエリ実行完了 (%d ms)\n抽出件数: %d 件", time, resultElements.size()), Toast.LENGTH_LONG).show();
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
        if (sharedPreferences.getBoolean("pref_use_binded_user", false)) {
            if (currentPage instanceof TimelineFragment) {
                TimelineFragment current = (TimelineFragment) this.currentPage;

                AuthUserRecord userRecord = null;
                List<FilterSource> sources = current.getQuery().getSources();
                for (FilterSource source : sources) {
                    AuthUserRecord sourceAccount = source.getSourceAccount();
                    if (sourceAccount != null) {
                        if (userRecord == null) {
                            userRecord = sourceAccount;
                        } else if (userRecord.InternalId != sourceAccount.InternalId) {
                            // 単一のアカウントに絞りこめない場合、この機能は発動させない
                            // (過去にシングルアカウントTLでのみ有効であったことに由来する互換措置)
                            userRecord = null;
                            break;
                        }
                    }
                }

                if (userRecord != null) {
                    intent.putExtra(TweetActivity.EXTRA_USER, userRecord);
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
        binding.pager.setCurrentItem(pageId);

        if (!pageList.isEmpty()) {
            WeakReference<Fragment> reference = tabRegistry.get(pageList.get(pageId).getId());
            if (reference != null) {
                currentPage = reference.get();
            }
            binding.tvMainTab.setText(pageList.get(pageId).getTitle());
            currentPage = tabPagerAdapter.instantiateItem(pageId);
            currentPageIndex = pageId;
        }
        else {
            binding.tvMainTab.setText("!EMPTY!");
        }
    }

    private void onTabChanged(int position) {
        TabInfo tabInfo = pageList.get(position);

        binding.tvMainTab.setText(tabInfo.getTitle());
        currentPage = tabPagerAdapter.instantiateItem(position);
        currentPageIndex = position;

        if (currentPage != null) {
            if (tabInfo.isCloseable()) {
                binding.ibClose.setVisibility(View.VISIBLE);
            } else {
                binding.ibClose.setVisibility(View.INVISIBLE);
            }
            if (currentPage instanceof TimelineFragment) {
                FilterQuery query = ((TimelineFragment) currentPage).getQuery();
                onQueryCompiled((TimelineFragment) currentPage, query);
            } else {
                binding.ibStream.setVisibility(View.INVISIBLE);
            }
        } else {
            Toast.makeText(getApplicationContext(), "Tab Change Error", Toast.LENGTH_LONG).show();
            binding.ibClose.setVisibility(View.INVISIBLE);
            binding.ibStream.setVisibility(View.INVISIBLE);
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
            AuthUserRecord userRecord = getTwitterService().findPreferredUser(Provider.API_TWITTER);
            if (userRecord == null) {
                Toast.makeText(getApplicationContext(), "Twitterアカウントが認証されていないため、検索を実行できません。", Toast.LENGTH_SHORT).show();
                return;
            }
            TabInfo tabInfo = new TabInfo(TabType.TABTYPE_SEARCH, pageList.size(), userRecord, searchQuery);
            tabInfo.setCloseable(true);
            addTab(tabInfo);
            binding.pager.getAdapter().notifyDataSetChanged();
            binding.pager.setCurrentItem(tabInfo.getOrder());
        }
        else {
            binding.pager.setCurrentItem(existId);
        }
    }

    private void showQuickPost() {
        binding.llQuickTweet.setVisibility(View.VISIBLE);

        QuickPostFragment quickPostFragment = (QuickPostFragment) getSupportFragmentManager().findFragmentById(R.id.flgQuickPost);
        if (quickPostFragment != null) {
            if (currentPage instanceof TimelineFragment) {
                StringBuilder defaultText = new StringBuilder();
                FilterQuery query = ((TimelineFragment) currentPage).getQuery();
                for (FilterSource source : query.getSources()) {
                    if (source instanceof Search) {
                        defaultText.append(" ").append(((Search) source).getQuery());
                    }
                }
                quickPostFragment.setDefaultText(defaultText.toString());
            } else {
                quickPostFragment.setDefaultText("");
            }
        }
    }

    @Override
    public void onCloseQuickPost() {
        binding.llQuickTweet.setVisibility(View.GONE);
    }

    @NonNull
    public List<shibafu.yukari.entity.Status> getStatusesList(String id) {
        if (!pageStatuses.containsKey(id)) {
            pageStatuses.put(id, new ArrayList<>());
        }
        return pageStatuses.get(id);
    }

    public void registerTwitterFragment(long id, Fragment fragment) {
        Log.d("MainActivity", "register tab: " + id);
        tabRegistry.put(id, new WeakReference<>(fragment));
    }

    public void unregisterTwitterFragment(long id) {
        Log.d("MainActivity", "unregister tab: " + id);
        tabRegistry.remove(id);
    }

    public void onQueryCompiled(TimelineFragment fragment, FilterQuery query) {
        if (fragment != currentPage) {
            return;
        }

        boolean hasDynamicChannel = false;
        boolean isStreaming = false;
        TwitterService service = getTwitterService();

        if (service != null) {
            for (FilterSource source : query.getSources()) {
                DynamicChannelController dcc = source.getDynamicChannelController();
                if (dcc != null) {
                    hasDynamicChannel = true;

                    AuthUserRecord userRecord = source.getSourceAccount();
                    if (userRecord == null) {
                        break;
                    }

                    ProviderStream stream = service.getProviderStream(userRecord);
                    if (stream == null) {
                        break;
                    }

                    isStreaming = dcc.isConnected(getApplicationContext(), stream) || isStreaming;
                    break;
                }
            }
        }

        if (hasDynamicChannel) {
            binding.ibStream.setVisibility(View.VISIBLE);
            if (isStreaming) {
                binding.ibStream.setImageResource(R.drawable.ic_play_d);
            } else {
                binding.ibStream.setImageResource(R.drawable.ic_pause_d);
            }
        } else {
            binding.ibStream.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onServiceConnected() {
        if (getTwitterService().getUsers().isEmpty()) {
            Intent intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            startActivityForResult(intent, REQUEST_OAUTH);
            finish();
            return;
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_POST_NOTIFICATIONS);
            }
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
            return (Fragment) super.instantiateItem(binding.pager, position);
        }

        public Fragment instantiateItem() {
            return (Fragment) super.instantiateItem(binding.pager, binding.pager.getCurrentItem());
        }
    }
}
