package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnTouch;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.*;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.FilterCompilerException;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.filter.compiler.TokenizeException;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.SearchDialogFragment;
import shibafu.yukari.fragment.tabcontent.DefaultTweetListFragment;
import shibafu.yukari.fragment.tabcontent.SearchListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.fragment.tabcontent.TwitterListFragment;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.twitter.streaming.FilterStream;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterResponse;
import twitter4j.util.CharacterUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class MainActivity extends ActionBarYukariBase implements SearchDialogFragment.SearchDialogCallback {

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_QPOST_CHOOSE_ACCOUNT = 3;
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

    private TwitterListFragment currentPage;
    private ArrayList<TabInfo> pageList = new ArrayList<>();
    private Map<Long, ArrayList<? extends TwitterResponse>> pageElements = new HashMap<>();
    @InjectView(R.id.tvMainTab)     TextView tvTabText;
    @InjectView(R.id.pager)         ViewPager viewPager;
    @InjectView(R.id.ibClose)       ImageButton ibClose;
    @InjectView(R.id.ibStream)      ImageButton ibStream;
    @InjectView(R.id.llTweetGuide)  LinearLayout llTweetGuide;
    @InjectView(R.id.streamState)   TriangleView tvStreamState;

    //QuickPost関連
    private InputMethodManager imm;
    private boolean enableQuickPost = true;
    private AuthUserRecord selectedAccount;
    @InjectView(R.id.llQuickTweet)  LinearLayout llQuickTweet;
    @InjectView(R.id.ibAccount)     ImageButton ibSelectAccount;
    @InjectView(R.id.etTweetInput)  EditText etTweet;

    //投稿ボタン関連
    @InjectView(R.id.tweetbutton_frame) FrameLayout flTweet;

    private TabPagerAdapter tabPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, getString(R.string.error_storage_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        else {
            try {
                if (FontAsset.checkFontFileExist(this, FontAsset.FONT_NAME)) {
                    Typeface.createFromFile(FontAsset.getFontFileExtPath(this, FontAsset.FONT_NAME));
                } else throw new FileNotFoundException("Font asset not found.");
            } catch (FileNotFoundException | RuntimeException e) {
                if (e instanceof RuntimeException) {
                    Toast.makeText(this, getString(R.string.error_broken_font), Toast.LENGTH_LONG).show();
                }
                Intent intent = new Intent(this, AssetExtractActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        }

        findViews();

        if (new File(getExternalFilesDir(null), "wallpaper").exists()) {
            Drawable wallpaper = Drawable.createFromPath(new File(getExternalFilesDir(null), "wallpaper").getAbsolutePath());
            wallpaper.setAlpha(72);
            viewPager.setBackground(wallpaper);
        }

        //スリープ防止設定
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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
        ButterKnife.inject(this);

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
                if (etTweet.getText().length() < 1 && currentPage instanceof SearchListFragment) {
                    etTweet.setText(" " + ((SearchListFragment) currentPage).getStreamFilter());
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
                        currentPage.scrollToTop();
                        return true;
                    case 1:
                        currentPage.scrollToBottom();
                        return true;
                    case 2:
                        startActivity(new Intent(MainActivity.this, TabEditActivity.class));
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
                        Intent intent = new Intent(MainActivity.this, AccountChooserActivity.class);
                        startActivityForResult(intent, REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT);
                        break;
                    }
                    case R.id.action_search_tweets: {
                        SearchDialogFragment dialogFragment = new SearchDialogFragment();
                        dialogFragment.show(getSupportFragmentManager(), "search");
                        break;
                    }
                    case R.id.action_search_users:
                        startActivity(new Intent(MainActivity.this, UserSearchActivity.class));
                        break;
                    case R.id.action_query:
                        startActivityForResult(new Intent(MainActivity.this, QueryEditorActivity.class), REQUEST_QUERY);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (ViewConfiguration.get(this).hasPermanentMenuKey()) {
                ibMenu.setVisibility(View.GONE);
            }
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            ibMenu.setVisibility(View.GONE);
        }

        ibClose.setOnLongClickListener(view -> {
            if (currentPage.isCloseable()) {
                int current = viewPager.getCurrentItem();
                TabInfo tabInfo = pageList.get(current);
                TwitterListFragment tabFragment = tabPagerAdapter.findFragmentByPosition(viewPager, current);
                if (tabFragment instanceof SearchListFragment &&
                        ((SearchListFragment) tabFragment).isStreaming()) {
                    getTwitterService().getStatusManager().stopFilterStream(tabInfo.getSearchKeyword());
                }

                pageList.remove(current);
                viewPager.setAdapter(new TabPagerAdapter(getSupportFragmentManager()));
                viewPager.setCurrentItem(current - 1);
                return true;
            } else return false;
        });
        ibClose.setOnClickListener(view -> {
            if (currentPage.isCloseable()) {
                Toast.makeText(MainActivity.this, "長押しでタブを閉じる", Toast.LENGTH_SHORT).show();
            }
        });

        ibStream.setOnClickListener(view -> {
            if (currentPage instanceof SearchListFragment) {
                boolean isStreaming = !((SearchListFragment) currentPage).isStreaming();
                ((SearchListFragment) currentPage).setStreaming(isStreaming);
                if (isStreaming) {
                    ibStream.setImageResource(R.drawable.ic_play);
                }
                else {
                    ibStream.setImageResource(R.drawable.ic_pause);
                }
            }
        });

        tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(tabPagerAdapter);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int i) {
                tvTabText.setText(pageList.get(i).getTitle());
                currentPage = tabPagerAdapter.findFragmentByPosition(viewPager, i);
                if (currentPage != null) {
                    if (currentPage.isCloseable()) {
                        ibClose.setVisibility(View.VISIBLE);
                    } else {
                        ibClose.setVisibility(View.INVISIBLE);
                    }
                    if (currentPage instanceof SearchListFragment) {
                        ibStream.setVisibility(View.VISIBLE);
                        if (((SearchListFragment) currentPage).isStreaming()) {
                            ibStream.setImageResource(R.drawable.ic_play);
                        } else {
                            ibStream.setImageResource(R.drawable.ic_pause);
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
            public void onPageScrollStateChanged(int i) {
            }
        });

        ImageButton ibCloseTweet = (ImageButton) findViewById(R.id.ibCloseTweet);
        ibCloseTweet.setOnClickListener(view -> {
            if (etTweet.getText().length() < 1) {
                llQuickTweet.setVisibility(View.GONE);
            } else {
                etTweet.setText("");
            }
        });
        ibSelectAccount.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AccountChooserActivity.class);
            startActivityForResult(intent, REQUEST_QPOST_CHOOSE_ACCOUNT);
        });
        etTweet.setOnKeyListener((view, i, keyEvent) -> {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                    keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                postTweet();
            }
            return false;
        });
        etTweet.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 装飾の除去
                Object[] spanned = s.getSpans(0, s.length(), Object.class);
                if (spanned != null) {
                    for (Object o : spanned) {
                        if (o instanceof CharacterStyle && (s.getSpanFlags(o) & Spanned.SPAN_COMPOSING) != Spanned.SPAN_COMPOSING) {
                            s.removeSpan(o);
                        }
                    }
                }
            }
        });
        etTweet.setTypeface(FontAsset.getInstance(this).getFont());
        ImageButton ibSendTweet = (ImageButton) findViewById(R.id.ibTweet);
        ibSendTweet.setOnClickListener(view -> postTweet());

        ImageView ivTweet = (ImageView) findViewById(R.id.ivTweet);
        ivTweet.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, TweetActivity.class)));
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
                    Intent intent = new Intent(MainActivity.this, TweetActivity.class);
                    if (sharedPreferences.getBoolean("pref_use_binded_user", false)
                            && currentPage != null
                            && currentPage instanceof DefaultTweetListFragment
                            && currentPage.getBoundUsers().size() == 1) {
                        switch (currentPage.getMode()) {
                            case TabType.TABTYPE_HOME:
                            case TabType.TABTYPE_MENTION:
                            case TabType.TABTYPE_DM:
                            case TabType.TABTYPE_LIST:
                                intent.putExtra(TweetActivity.EXTRA_USER, currentPage.getCurrentUser());
                                break;
                        }
                    }
                    startActivity(intent);
                    return true;
                }
                break;
        }
        return v.getId() == R.id.tweetgesture;
    }

    @OnClick(R.id.llMainFooterRight)
    void onClickFooterSpace() {
        if (currentPage != null && sharedPreferences.getBoolean("pref_quick_scroll_to_top", false)) {
            currentPage.scrollToTop();
        }
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
        outState.putInt("currentId", viewPager.getCurrentItem());
        outState.putSerializable("tabinfo", pageList);

        Log.d("MainActivity", "call onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d("MainActivity", "call onRestoreInstanceState");

        keepScreenOn = savedInstanceState.getBoolean("screen");
        immersive = savedInstanceState.getBoolean("immersive");
        currentPage = (TwitterListFragment) getSupportFragmentManager().getFragment(savedInstanceState, "current");
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
        tabPagerAdapter = null;
        viewPager = null;
        imm = null;
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("MainActivity", "call onActivityResult | request=" + requestCode + ", result=" + resultCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_QPOST_CHOOSE_ACCOUNT:
                {
                    selectedAccount = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    ImageLoaderTask.loadProfileIcon(getApplicationContext(), ibSelectAccount, selectedAccount.ProfileImageUrl);
                    break;
                }
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
                            Twitter twitter = getTwitterService().getTwitter();
                            twitter.setOAuthAccessToken(params[0].account.getAccessToken());
                            try {
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
                        FilterQuery query = QueryCompiler.compile(users, data.getStringExtra(Intent.EXTRA_TEXT));
                        TabInfo tabInfo = new TabInfo(
                                TabType.TABTYPE_FILTER, pageList.size(), getTwitterService().getPrimaryUser(), data.getStringExtra(Intent.EXTRA_TEXT));
                        ArrayList<TwitterResponse> elements = new ArrayList<>(pageElements.get(pageList.get(viewPager.getCurrentItem()).getId()));
                        for (Iterator<TwitterResponse> iterator = elements.iterator(); iterator.hasNext(); ) {
                            TwitterResponse element = iterator.next();
                            if (!query.evaluate(element, users)) {
                                iterator.remove();
                            }
                        }
                        pageElements.put(tabInfo.getId(), elements);
                        addTab(tabInfo);
                        viewPager.getAdapter().notifyDataSetChanged();
                        viewPager.setCurrentItem(tabInfo.getOrder());

                        time = System.currentTimeMillis() - time;
                        Toast.makeText(getApplicationContext(), String.format("クエリ実行完了\n処理時間: %d ms\n抽出件数: %d 件", time, elements.size()), Toast.LENGTH_LONG).show();
                    } catch (FilterCompilerException | TokenizeException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
                    }
                    break;
                }
            }
        }
    }

    private void postTweet() {
        if (selectedAccount == null) {
            Toast.makeText(this, "アカウントが選択されていません", Toast.LENGTH_LONG).show();
        }
        else if (etTweet.getText().length() < 1) {
            if (currentPage instanceof SearchListFragment) {
                etTweet.append(" " + ((SearchListFragment) currentPage).getStreamFilter());
            } else {
                Toast.makeText(this, "テキストが入力されていません", Toast.LENGTH_LONG).show();
            }
        }
        else if (selectedAccount != null && CharacterUtil.count(etTweet.getText().toString()) <= 140) {
            //ドラフト生成
            TweetDraft draft = new TweetDraft(
                    selectedAccount.toSingleList(),
                    etTweet.getText().toString(),
                    System.currentTimeMillis(),
                    -1,
                    false,
                    null,
                    false,
                    0,
                    0,
                    false,
                    false);

            //サービス起動
            startService(PostService.newIntent(this, draft));

            //投稿欄を掃除する
            etTweet.setText("");
            if (currentPage instanceof SearchListFragment) {
                etTweet.append(" " + ((SearchListFragment) currentPage).getStreamFilter());
            }
            etTweet.requestFocus();
            imm.showSoftInput(etTweet, InputMethodManager.SHOW_FORCED);
        }
    }

    private void addTab(TabInfo tabInfo) {
        pageList.add(tabInfo);
    }

    private void initTabs(boolean reload) {
        int pageId = 0;
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
        else for (int i = 0; i < pageList.size(); ++i) {
            if (tabPagerAdapter.findFragmentByPosition(viewPager, i) == currentPage) {
                pageId = i;
                break;
            }
        }

        tabPagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(pageId);

        if (!pageList.isEmpty()) {
            currentPage = tabPagerAdapter.findFragmentByPosition(viewPager, pageId);
            tvTabText.setText(pageList.get(pageId).getTitle());
        }
        else {
            tvTabText.setText("!EMPTY!");
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

    public <T extends TwitterResponse> ArrayList<T> getElementsList(long id) {
        if (!pageElements.containsKey(id)) {
            pageElements.put(id, new ArrayList<T>());
        }
        return (ArrayList<T>) pageElements.get(id);
    }

    @Override
    public void onServiceConnected() {
        if (getTwitterService().getUsers().isEmpty()) {
            Intent intent = new Intent(MainActivity.this, OAuthActivity.class);
            intent.putExtra(OAuthActivity.EXTRA_REBOOT, true);
            startActivityForResult(intent, REQUEST_OAUTH);
            finish();
        }
        else {
            initTabs(pageList.isEmpty());

            if (selectedAccount == null) {
                selectedAccount = getTwitterService().getPrimaryUser();
                if (selectedAccount == null) {
                    Toast.makeText(MainActivity.this, "プライマリアカウントが取得できません\nクイック投稿は無効化されます", Toast.LENGTH_LONG).show();
                    enableQuickPost = false;
                }
                else if (ibSelectAccount == null) {
                    Toast.makeText(MainActivity.this, "UIの初期化に失敗しているようです\nクイック投稿は無効化されます", Toast.LENGTH_LONG).show();
                    enableQuickPost = false;
                }
                else {
                    ImageLoaderTask.loadProfileIcon(getApplicationContext(), ibSelectAccount, selectedAccount.ProfileImageUrl);
                    enableQuickPost = true;
                }
            }

            //UserStreamを開始する
            StatusManager statusManager = getTwitterService().getStatusManager();
            if (statusManager != null && !statusManager.isStarted()) {
                statusManager.start();
            }

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

            TwitterListFragment fragment = TweetListFragmentFactory.newInstance(tabInfo);
            switch (tabInfo.getType()) {
                case TabType.TABTYPE_TRACK:
                    //現状ここに行き着くことってそんなに無い気がする
                    getTwitterService().getStatusManager().startFilterStream(
                            new FilterStream.ParsedQuery(tabInfo.getSearchKeyword()).getValidQuery(),
                            tabInfo.getBindAccount());
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

        public TwitterListFragment findFragmentByPosition(ViewPager viewPager,
                                               int position) {
            return (TwitterListFragment) instantiateItem(viewPager, position);
        }
    }
}
