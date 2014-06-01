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
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.MenuDialogFragment;
import shibafu.yukari.fragment.SearchDialogFragment;
import shibafu.yukari.fragment.tabcontent.SearchListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.fragment.tabcontent.TwitterListFragment;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StatusManager;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.util.CharacterUtil;

public class MainActivity extends ActionBarActivity implements TwitterServiceDelegate, SearchDialogFragment.SearchDialogCallback {

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_FRIEND_CACHE = 2;
    private static final int REQUEST_QPOST_CHOOSE_ACCOUNT = 3;
    private static final int REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT = 4;

    public static final String EXTRA_SEARCH_WORD = "searchWord";
    public static final String EXTRA_SHOW_TAB = "showTab";

    private SharedPreferences sharedPreferences;
    private TwitterService service;
    private boolean serviceBound = false;

    private boolean keepScreenOn = false;

    private boolean isTouchTweet = false;
    private float tweetGestureYStart = 0;
    private float tweetGestureY = 0;

    private TwitterListFragment currentPage;
    private ArrayList<TabInfo> pageList = new ArrayList<>();
    private TextView tvTabText;
    private ViewPager viewPager;
    private ImageButton ibClose, ibStream;
    private LinearLayout llTweetGuide;

    //QuickPost関連
    private InputMethodManager imm;
    private boolean enableQuickPost = true;
    private AuthUserRecord selectedAccount;
    private LinearLayout llQuickTweet;
    private ImageButton ibSelectAccount;
    private EditText etTweet;

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
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this,
                    "[Yukari 起動エラー] ストレージエラー\nアプリの動作にはストレージが必須です\nSDカードが挿入されているか確認してください",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        else if (!FontAsset.checkFontFileExt(this)) {
            Intent intent = new Intent(this, AssetExtractActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        findViews();

        //スリープ防止設定
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setKeepScreenOn(sharedPreferences.getBoolean("pref_boot_screenon", false));
    }

    private void findViews() {
        TextView tvStreamStates = (TextView) findViewById(R.id.tvStreamStates);
        tvStreamStates.setOnTouchListener(tweetGestureListener);
        tvStreamStates.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (enableQuickPost) {
                    llQuickTweet.setVisibility(View.VISIBLE);
                    if (etTweet.getText().length() < 1 && currentPage instanceof SearchListFragment) {
                        etTweet.setText(" " + ((SearchListFragment) currentPage).getStreamFilter());
                    }
                    return true;
                }
                else return false;
            }
        });

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
                menu.add(Menu.NONE, 0, 0, "⇧ TLの一番上へ");
                menu.add(Menu.NONE, 2, 1, "◇ タブの編集");
                menu.add(Menu.NONE, 1, 9, "⇩ TLの一番下へ");
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
                            case 2:
                                startActivity(new Intent(MainActivity.this, TabEditActivity.class));
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
                if (currentPage instanceof SearchListFragment) {
                    popupMenu.getMenu().findItem(R.id.action_save_search).setVisible(true);
                }
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_save_search:
                            {
                                Intent intent = new Intent(MainActivity.this, AccountChooserActivity.class);
                                startActivityForResult(intent, REQUEST_SAVE_SEARCH_CHOOSE_ACCOUNT);
                                break;
                            }
                            case R.id.action_search_tweets:
                            {
                                SearchDialogFragment dialogFragment = new SearchDialogFragment();
                                dialogFragment.show(getSupportFragmentManager(), "search");
                                break;
                            }
                            case R.id.action_search_users:
                                startActivity(new Intent(MainActivity.this, UserSearchActivity.class));
                                break;
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

                                        if (sn == null || sn.length() < 1) {
                                            Toast.makeText(MainActivity.this, "何も入力されていません", Toast.LENGTH_LONG).show();
                                        }
                                        else {
                                            if (sn.startsWith("@")) {
                                                sn = sn.substring(1);
                                            }

                                            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                            intent.setData(Uri.parse("http://twitter.com/" + sn));
                                            intent.putExtra(ProfileActivity.EXTRA_USER, serviceBound?service.getPrimaryUser():null);
                                            startActivity(intent);
                                        }
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
                    TabInfo tabInfo = pageList.get(current);
                    if (tabInfo.getListFragment() instanceof SearchListFragment &&
                            ((SearchListFragment) tabInfo.getListFragment()).isStreaming()) {
                        service.getStatusManager().stopFilterStream(tabInfo.getSearchKeyword());
                    }

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

        ibStream = (ImageButton) findViewById(R.id.ibStream);
        ibStream.setOnTouchListener(tweetGestureListener);
        ibStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                currentPage = pageList.get(i).getListFragment();
                if (currentPage.isCloseable()) {
                    ibClose.setVisibility(View.VISIBLE);
                }
                else {
                    ibClose.setVisibility(View.INVISIBLE);
                }
                if (currentPage instanceof SearchListFragment) {
                    ibStream.setVisibility(View.VISIBLE);
                    if (((SearchListFragment) currentPage).isStreaming()) {
                        ibStream.setImageResource(R.drawable.ic_play);
                    }
                    else {
                        ibStream.setImageResource(R.drawable.ic_pause);
                    }
                }
                else {
                    ibStream.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        llQuickTweet = (LinearLayout) findViewById(R.id.llQuickTweet);
        ImageButton ibCloseTweet = (ImageButton) findViewById(R.id.ibCloseTweet);
        ibCloseTweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (etTweet.getText().length() < 1) {
                    llQuickTweet.setVisibility(View.GONE);
                } else {
                    etTweet.setText("");
                }
            }
        });
        ibSelectAccount = (ImageButton) findViewById(R.id.ibAccount);
        ibSelectAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_QPOST_CHOOSE_ACCOUNT);
            }
        });
        etTweet = (EditText) findViewById(R.id.etTweetInput);
        etTweet.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    postTweet();
                }
                return false;
            }
        });
        etTweet.setTypeface(FontAsset.getInstance(this).getFont());
        ImageButton ibSendTweet = (ImageButton) findViewById(R.id.ibTweet);
        ibSendTweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                postTweet();
            }
        });

        llTweetGuide = (LinearLayout) findViewById(R.id.llTweetGuide);
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
                        break;
                    }
                }
            }
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
    protected void onResume() {
        super.onResume();
        //ツイート操作ガイド
        llTweetGuide.setVisibility(sharedPreferences.getBoolean("first_guide", true)? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("screen", keepScreenOn);
        if (currentPage != null && !currentPage.isDetached()) {
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
        currentPage = (TweetListFragment) getSupportFragmentManager().getFragment(savedInstanceState, "current");
        pageList = (ArrayList<TabInfo>) savedInstanceState.getSerializable("tabinfo");
        int currentId = savedInstanceState.getInt("currentId", -1);
        for (int i = 0; i < pageList.size(); i++) {
            TabInfo tabInfo = pageList.get(i);
            if (i == currentId) {
                tabInfo.setListFragment(currentPage);
            }
            else if (tabInfo.getListFragment() == null) {
                tabInfo.setListFragment(TweetListFragmentFactory.newInstance(tabInfo));
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
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
                case REQUEST_QPOST_CHOOSE_ACCOUNT:
                {
                    selectedAccount = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    ImageLoaderTask.loadProfileIcon(MainActivity.this, ibSelectAccount, selectedAccount.ProfileImageUrl);
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
                    new TwitterAsyncTask<Args>() {
                        @Override
                        protected TwitterException doInBackground(Args... params) {
                            Twitter twitter = service.getTwitter();
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
            }
            else {
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
        TwitterListFragment fragment = TweetListFragmentFactory.newInstance(tabInfo);
        switch (tabInfo.getType()) {
            case TabType.TABTYPE_TRACK:
                service.getStatusManager().startFilterStream(tabInfo.getSearchKeyword(), tabInfo.getBindAccount());
                break;
        }
        tabInfo.setListFragment(fragment);

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
        else for (int i = 0; i < pageList.size(); ++i) {
            if (pageList.get(i).getListFragment() == currentPage) {
                pageId = i;
                break;
            }
        }

        TabPagerAdapter adapter = new TabPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(pageId);

        if (!pageList.isEmpty()) {
            currentPage = pageList.get(pageId).getListFragment();
            tvTabText.setText(pageList.get(pageId).getTitle());
        }
        else {
            tvTabText.setText("!EMPTY!");
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("MainActivity", "onServiceConnected");
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

                if (selectedAccount == null) {
                    selectedAccount = MainActivity.this.service.getPrimaryUser();
                    if (selectedAccount == null) {
                        Toast.makeText(MainActivity.this, "プライマリアカウントが取得できません\nクイック投稿は無効化されます", Toast.LENGTH_LONG).show();
                        enableQuickPost = false;
                    }
                    else if (ibSelectAccount == null) {
                        Toast.makeText(MainActivity.this, "UIの初期化に失敗しているようです\nクイック投稿は無効化されます", Toast.LENGTH_LONG).show();
                        enableQuickPost = false;
                    }
                    else {
                        ImageLoaderTask.loadProfileIcon(MainActivity.this, ibSelectAccount, selectedAccount.ProfileImageUrl);
                        enableQuickPost = true;
                    }
                }

                //UserStreamを開始する
                StatusManager statusManager = MainActivity.this.service.getStatusManager();
                if (statusManager != null && !statusManager.isStarted()) {
                    statusManager.start();
                }

                onNewIntent(getIntent());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("MainActivity", "onServiceDisconnected");
            serviceBound = false;
        }
    };

    @Override
    public TwitterService getTwitterService() {
        return service;
    }

    @Override
    public void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking) {
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
                    TabType.TABTYPE_SEARCH, pageList.size(), service.getPrimaryUser(), searchQuery);
            addTab(tabInfo);
            viewPager.getAdapter().notifyDataSetChanged();
            viewPager.setCurrentItem(tabInfo.getOrder());
        }
        else {
            viewPager.setCurrentItem(existId);
        }
    }

    class TabPagerAdapter extends FragmentStatePagerAdapter {

        public TabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return pageList.get(i).getListFragment();
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
