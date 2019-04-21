package shibafu.yukari.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPopupHelper;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sys1yagi.mastodon4j.api.entity.Status.Visibility;
import com.twitter.Extractor;
import info.shibafu528.gallerymultipicker.MultiPickerActivity;
import info.shibafu528.yukari.exvoice.MRubyException;
import info.shibafu528.yukari.exvoice.ProcWrapper;
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;
import permissions.dispatcher.RuntimePermissions;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.UsedHashes;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.database.Provider;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.fragment.DraftDialogFragment;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.fragment.SimpleListDialogFragment;
import shibafu.yukari.linkage.PostValidator;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.linkage.ProviderApiException;
import shibafu.yukari.mastodon.entity.DonStatus;
import shibafu.yukari.plugin.MorseInputActivity;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TweetValidator;
import shibafu.yukari.twitter.TwitterApi;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.util.AttrUtil;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.StringUtil;
import shibafu.yukari.util.ThemeUtil;
import shibafu.yukari.util.TweetPreprocessor;
import twitter4j.TwitterAPIConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.BreakIterator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RuntimePermissions
public class TweetActivity extends ActionBarYukariBase implements DraftDialogFragment.DraftDialogEventListener, SimpleAlertDialogFragment.OnDialogChoseListener, SimpleListDialogFragment.OnDialogChoseListener {

    public static final int MODE_TWEET = 0;
    public static final int MODE_REPLY = 1;
    public static final int MODE_DM = 2;
    public static final int MODE_QUOTE = 3;
    public static final int MODE_COMPOSE = 4;

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_WRITERS = "writers";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_IN_REPLY_TO = "in_reply_to";
    public static final String EXTRA_DM_TARGET_SN = "target_sn";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MEDIA = "media";
    public static final String EXTRA_GEO_LOCATION = "geo";
    public static final String EXTRA_DRAFT = "draft";
    public static final String EXTRA_VISIBILITY = "visibility";
    public static final String EXTRA_SPOILER_TEXT = "spoiler_text";

    private static final int REQUEST_GALLERY = 0x01;
    private static final int REQUEST_CAMERA = 0x02;
    private static final int REQUEST_VOICE = 0x03;
    private static final int REQUEST_NOWPLAYING = 0x21;
    private static final int REQUEST_TOTSUSI = 0x22;
    private static final int REQUEST_SNPICKER = 0x23;
    private static final int REQUEST_TWICCA_PLUGIN = 0x41;
    private static final int REQUEST_ACCOUTS = 0x81;

    private static final int REQUEST_DIALOG_CLEAR = 0x01;
    private static final int REQUEST_DIALOG_YUKARIN = 0x02;
    private static final int REQUEST_DIALOG_TEMPLATE = 0x03;
    private static final int REQUEST_DIALOG_POST = 0x04;
    private static final int REQUEST_DIALOG_BACK = 0x05;
    private static final int REQUEST_DIALOG_HASH_CATEGORY = 0x06;
    private static final int REQUEST_DIALOG_HASH_VALUE = 0x07;
    private static final int REQUEST_DIALOG_DRAFT_MENU = 0x08;

    // PermissionsDispatcherが若いリクエスト番号を使うので、自前実装は大きめの番号から。
    private static final int PERMISSION_REQUEST_INIT_ATTACH = 0x1000;

    private static final int PLUGIN_ICON_DIP = 28;

    private static final int TITLE_AREA_MAX_LINES = 3;

    private static final Pattern PATTERN_PREFIX = Pattern.compile("(@[0-9a-zA-Z_]{1,15} )+.*");
    private static final Pattern PATTERN_SUFFIX = Pattern.compile(".*( (RT |QT |\")@[0-9a-zA-Z_]{1,15}: .+)");
    private static final Pattern PATTERN_QUOTE = Pattern.compile(" https?://(mobile\\.|www\\.)?twitter\\.com/[0-9a-zA-Z_]{1,15}/status(es)?/\\d+/?$");

    private static final Extractor EXTRACTOR = new Extractor();

    //タイトル部
    private TextView tvTitle;
    private boolean isExpandedTitleArea = false;

    //入力欄カウント系
    private EditText etInput;
    private TextView tvCount;
    private List<PostValidator> validators = new ArrayList<>();

    //DMフラグ
    @NeedSaveState private boolean isDirectMessage = false;
    @NeedSaveState private long directMessageDestId;
    @NeedSaveState private String directMessageDestSN;

    //編集モード
    @NeedSaveState private boolean isComposerMode = false;

    //添付プレビュー
    private ImageButton ibAttach;
    private ImageButton ibCamera;
    private LinearLayout llTweetAttachParent;
    private LinearLayout llTweetAttachInner;
    private CheckBox cbSensitive;

    //Writer指定
    @NeedSaveState private ArrayList<AuthUserRecord> writers = new ArrayList<>();
    //アカウントのWriter指定を使用する(Writerアカウント指定呼び出しの場合は折る)
    @NeedSaveState private boolean useStoredWriters = true;

    private Status status;
    @NeedSaveState private List<AttachPicture> attachPictures = new ArrayList<>();

    private SharedPreferences sp;

    //短縮URLの文字数
    private int shortUrlLength = 23;

    //最大添付数
    private int maxMediaPerUpload = 4;

    //撮影用の一時変数
    @NeedSaveState private Uri cameraTemp;

    private AlertDialog currentDialog;

    //ScreenNameピッカーの呼び出しボタン
    private ImageButton ibSNPicker;

    //Writer一覧ビュー
    private TextView tvTweetBy;
    private Button btnPost;

    //解決済みリソース
    private int tweetCountColor;
    private int tweetCountOverColor;

    //最近使ったハッシュタグ
    private UsedHashes usedHashes;

    //初期状態のスナップショット
    @NeedSaveState private StatusDraft initialDraft;

    //プラグインエリア
    private LinearLayout llTweetExtra;

    //Pluggaloidロード状態
    private boolean isLoadedPluggaloid;

    //テーマ
    private boolean usingDarkTheme;

    //可視性
    @NeedSaveState private StatusDraft.Visibility visibility = StatusDraft.Visibility.PUBLIC;
    private ImageButton ibVisibility;

    //Spoiler (Content Warning)
    private ImageButton ibSpoiler;
    private EditText etSpoiler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String theme = sp.getString("pref_theme", "light");
        switch (theme) {
            default:
                setTheme(R.style.ColorsTheme_Light_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "dark":
                setTheme(R.style.ColorsTheme_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
            case "akari":
                setTheme(R.style.ColorsTheme_Akari_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "akari_dark":
                setTheme(R.style.ColorsTheme_Akari_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
            case "zunko":
                setTheme(R.style.ColorsTheme_Zunko_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "zunko_dark":
                setTheme(R.style.ColorsTheme_Zunko_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
            case "maki":
                setTheme(R.style.ColorsTheme_Maki_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "maki_dark":
                setTheme(R.style.ColorsTheme_Maki_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
            case "aoi":
                setTheme(R.style.ColorsTheme_Aoi_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "aoi_dark":
                setTheme(R.style.ColorsTheme_Aoi_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
            case "akane":
                setTheme(R.style.ColorsTheme_Akane_Dialog_VertAnimation);
                usingDarkTheme = false;
                break;
            case "akane_dark":
                setTheme(R.style.ColorsTheme_Akane_Dark_Dialog_VertAnimation);
                usingDarkTheme = true;
                break;
        }
        super.onCreate(savedInstanceState, true);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_tweet);

        //リソースIDを解決
        tweetCountColor = ResourcesCompat.getColor(getResources(), AttrUtil.resolveAttribute(getTheme(), R.attr.tweetCountColor), getTheme());
        tweetCountOverColor = ResourcesCompat.getColor(getResources(), AttrUtil.resolveAttribute(getTheme(), R.attr.tweetCountOverColor), getTheme());

        //最近使ったハッシュタグのロード
        usedHashes = new UsedHashes(getApplicationContext());

        //Viewの初期化
        initializeViews();

        //IMEの表示設定
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        //ウィンドウ外のタッチで閉じないようにする
        setFinishOnTouchOutside(false);

        //Extraを取得
        final Intent args = getIntent();

        //statusを取得する (EXTRA_STATUS)
        Object status = args.getSerializableExtra(EXTRA_STATUS);
        if (status instanceof PreformedStatus) {
            this.status = new TwitterStatus((PreformedStatus) status, ((PreformedStatus) status).getRepresentUser());
        } else {
            this.status = (Status) status;
        }

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            initializeState();
        }
    }

    /**
     * 起動パラメータを基に初期化
     */
    private void initializeState() {
        //Extraを取得
        final Intent args = getIntent();
        Uri dataArg = args.getData();

        //ユーザー指定がある場合は表示する (EXTRA_USER, EXTRA_WRITERS)
        AuthUserRecord user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);
        ArrayList<AuthUserRecord> argWriters = (ArrayList<AuthUserRecord>) args.getSerializableExtra(EXTRA_WRITERS);
        if (argWriters != null) {
            useStoredWriters = false;
            writers = argWriters;
            updateWritersView();
        } else if (user != null) {
            useStoredWriters = false;
            writers.add(user);
            updateWritersView();
        }

        //WebIntent判定
        boolean isWebIntent = false;
        if (dataArg != null && "twitter.com".equals(dataArg.getHost())) {
            isWebIntent = true;
        }

        //DM判定
        isDirectMessage = args.getIntExtra(EXTRA_MODE, MODE_TWEET) == MODE_DM;
        if (isDirectMessage) {
            directMessageDestId = args.getLongExtra(EXTRA_IN_REPLY_TO, -1);
            directMessageDestSN = args.getStringExtra(EXTRA_DM_TARGET_SN);
            //表題変更
            tvTitle.setText("DirectMessage to @" + directMessageDestSN);
            //ボタン無効化と表示変更
            ibAttach.setEnabled(false);
            ibCamera.setEnabled(false);
            btnPost.setText("Send");
        }

        //デフォルト文章が設定されている場合は入力しておく (EXTRA_TEXT)
        String defaultText;
        if (isWebIntent) {
            String paramInReplyTo = dataArg.getQueryParameter("in_reply_to");
            String paramText = Optional.ofNullable(dataArg.getQueryParameter("text")).orElse("").replaceAll("%0[dD]", "\r").replaceAll("%0[aA]", "\n");
            String paramUrl = dataArg.getQueryParameter("url");
            String paramHashtags = dataArg.getQueryParameter("hashtags");

            StringBuilder sb = new StringBuilder(paramText);
            if (paramUrl != null) {
                sb.append(" ");
                sb.append(paramUrl);
            }
            if (paramHashtags != null) {
                String[] tags = paramHashtags.split(",");
                for (String t : tags) {
                    sb.append(" #");
                    sb.append(t);
                }
            }
            defaultText = sb.toString();
        } else if (Intent.ACTION_SEND.equals(args.getAction())) {
            defaultText = args.getDataString();
            if (defaultText == null) {
                defaultText = args.getStringExtra(Intent.EXTRA_TEXT);
            }
            if (args.hasExtra(Intent.EXTRA_TITLE)) {
                defaultText = args.getStringExtra(Intent.EXTRA_TITLE) + " " + defaultText;
            } else if (args.hasExtra(Intent.EXTRA_SUBJECT)) {
                boolean insertSubject = true;
                String subject = args.getStringExtra(Intent.EXTRA_SUBJECT);

                // 「スクショ共有の本文を削除」オプションの判定
                if (sp.getBoolean("pref_remove_screenshot_subject", false) && subject.startsWith("Screenshot (")) {
                    insertSubject = false;
                }
                // EXTRA_TEXTと内容が完全に被るなら要らない
                if (defaultText != null && defaultText.contains(subject)) {
                    insertSubject = false;
                }

                if (insertSubject) {
                    defaultText = subject + " " + defaultText;
                }
            }
        } else {
            defaultText = args.getStringExtra(EXTRA_TEXT);
        }
        int restoredTagsLength = 0; // 実況モードのタグを復元した場合、カーソル初期位置の扱いを考慮する必要がある
        if (sp.getBoolean("pref_save_tags", false)) {
            List<String> tags = new Gson().fromJson(sp.getString("pref_saved_tags", "[]"), new TypeToken<List<String>>() {}.getType());
            if (!tags.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String tag : tags) {
                    if (sb.length() > 1) {
                        sb.append(" ");
                    }
                    sb.append("#");
                    sb.append(tag);
                }
                restoredTagsLength = sb.length() + 1;
                sb.insert(0, " ");
                sb.insert(0, TextUtils.isEmpty(defaultText) ? "" : defaultText);
                defaultText = sb.toString();
            }
        }
        etInput.setText((defaultText != null) ? defaultText : sp.getString("pref_tweet_footer", ""));
        switch (args.getIntExtra(EXTRA_MODE, MODE_TWEET)) {
            case MODE_REPLY:
                etInput.setSelection(etInput.getText().length() - restoredTagsLength);
                // 返信先がMastodonのトゥートの場合、そのトゥートの可視性を初期値として引き継ぐ
                if (status instanceof DonStatus) {
                    String visibility = ((DonStatus) status).getStatus().getVisibility();
                    if (Visibility.Public.getValue().equals(visibility)) {
                        this.visibility = StatusDraft.Visibility.PUBLIC;
                    } else if (Visibility.Unlisted.getValue().equals(visibility)) {
                        this.visibility = StatusDraft.Visibility.UNLISTED;
                    } else if (Visibility.Private.getValue().equals(visibility)) {
                        this.visibility = StatusDraft.Visibility.PRIVATE;
                    } else if (Visibility.Direct.getValue().equals(visibility)) {
                        this.visibility = StatusDraft.Visibility.DIRECT;
                    }
                    setVisibility(this.visibility.ordinal());
                }
                /* fall through */
            case MODE_QUOTE: {
                // TODO: ReplyかつinReplyToIDで復帰するパターンは下書きからのロードのみ
                String inReplyTo = args.getStringExtra(EXTRA_IN_REPLY_TO);
                if (status == null && !TextUtils.isEmpty(inReplyTo)) {
                    tvTitle.setText("Reply >> loading...");
                    new SimpleAsyncTask() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                while (!isTwitterServiceBound()) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException ignore) {}
                                }

                                AuthUserRecord u;
                                if (user == null) {
                                    if (writers.isEmpty()) {
                                        return null;
                                    } else {
                                        u = writers.get(0);
                                    }
                                } else {
                                    u = user;
                                }

                                final ProviderApi api = getTwitterService().getProviderApi(u);
                                if (api == null) {
                                    return null;
                                }
                                status = api.showStatus(u, inReplyTo);
                            } catch (ProviderApiException ignored) {}
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            showQuotedStatus();
                        }
                    }.executeParallel();
                } else if (status != null) {
                    showQuotedStatus();
                }
                break;
            }
            case MODE_COMPOSE:
                isComposerMode = true;
                tvTitle.setText("Compose");
                break;
        }

        //添付データがある場合は設定する
        ArrayList<String> mediaUri = args.getStringArrayListExtra(EXTRA_MEDIA);
        if (args.getAction() != null && args.getType() != null &&
                args.getAction().equals(Intent.ACTION_SEND) && args.getType().startsWith("image/")) {
            attachPicture(args.getParcelableExtra(Intent.EXTRA_STREAM));
        } else if (mediaUri != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                for (String s : mediaUri) {
                    attachPicture(Uri.parse(s));
                }
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_INIT_ATTACH);
            }
        }

        // 可視性の設定
        setVisibility(args.getIntExtra(EXTRA_VISIBILITY, StatusDraft.Visibility.PUBLIC.ordinal()));

        // Spoilerの設定
        String spoilerText = args.getStringExtra(EXTRA_SPOILER_TEXT);
        if (!TextUtils.isEmpty(spoilerText)) {
            etSpoiler.setText(spoilerText);
            etSpoiler.setVisibility(View.VISIBLE);
        }

        // 初期化完了時点での下書き状況のスナップショット
        initialDraft = getTweetDraft().copyForJava();
    }

    /**
     * ビューの初期化
     */
    private void initializeViews() {
        //タイトル部の設定
        tvTitle = (TextView) findViewById(R.id.tvTweetTitle);
        tvTitle.setMaxLines(TITLE_AREA_MAX_LINES);
        tvTitle.setOnClickListener(v -> {
            if (isExpandedTitleArea) {
                tvTitle.setMaxLines(TITLE_AREA_MAX_LINES);
            } else {
                tvTitle.setMaxLines(Integer.MAX_VALUE);
            }
            isExpandedTitleArea = !isExpandedTitleArea;
        });

        //アカウント表示の設定
        tvTweetBy = (TextView) findViewById(R.id.tvTweetBy);
        tvTweetBy.setOnClickListener(v -> {
            Intent intent = new Intent(TweetActivity.this, AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE_QUICK_SELECT, true);
            intent.putExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS, writers);
            startActivityForResult(intent, REQUEST_ACCOUTS);
        });

        //テキストエリアの設定
        tvCount = (TextView) findViewById(R.id.tvTweetCount);
        tvCount.setOnLongClickListener(v -> {
            etInput.append(StringUtil.getVersionInfo(getApplicationContext()));
            return true;
        });
        etInput = (EditText) findViewById(R.id.etTweetInput);
        etInput.setTypeface(FontAsset.getInstance(this).getFont());
        etInput.setTextSize(Integer.valueOf(sp.getString("pref_font_input", "14")));
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateTweetCount();

                String text = s.toString();
                if (ibSNPicker != null) {
                    if (!text.isEmpty() && etInput.getSelectionStart() > 0 &&
                            text.charAt(etInput.getSelectionStart() - 1) == '@') {
                        ibSNPicker.setVisibility(View.VISIBLE);
                    } else {
                        ibSNPicker.setVisibility(View.GONE);
                    }
                }

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
        etInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        etSpoiler = findViewById(R.id.etTweetSpoiler);
        etSpoiler.setTypeface(FontAsset.getInstance(this).getFont());
        etSpoiler.setTextSize(Integer.valueOf(sp.getString("pref_font_input", "14")));
        etSpoiler.addTextChangedListener(new TextWatcher() {
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
        etSpoiler.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        //投稿ボタンの設定
        btnPost = (Button) findViewById(R.id.btnTweet);
        btnPost.setOnClickListener(v -> {
            if (sp.getBoolean("pref_dialog_post", false)) {
                SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                        REQUEST_DIALOG_POST, "確認", "ツイートしますか？", "OK", "キャンセル"
                );
                dialogFragment.show(getSupportFragmentManager(), "dialog");
            } else {
                postTweet();
            }
        });
        btnPost.setOnLongClickListener(v -> {
            SimpleAlertDialogFragment dialogFragment = new SimpleAlertDialogFragment.Builder(REQUEST_DIALOG_YUKARIN)
                    .setMessage("ゆっかりーん？")
                    .setPositive("\\ﾕｯｶﾘｰﾝ/")
                    .setNegative("(メ'ω')No")
                    .setDisableCaps(true)
                    .build();
            dialogFragment.show(getSupportFragmentManager(), "yukarindlg");
            return true;
        });

        //添付エリアの設定
        llTweetAttachParent = (LinearLayout) findViewById(R.id.llTweetAttachParent);
        llTweetAttachInner = (LinearLayout) findViewById(R.id.llTweetAttachInner);
        cbSensitive = (CheckBox) findViewById(R.id.cbTweetSensitive);

        // アクションエリアの初期化
        initializeActions();

        // プラグインエリアの初期化
        initializeEditPlugins();
    }

    /**
     * アクションエリアの初期化
     */
    private void initializeActions() {
        // ギャラリーボタン
        ibAttach = (ImageButton) findViewById(R.id.ibTweetAttachPic);
        ibAttach.setOnClickListener(v -> {
            TweetActivityPermissionsDispatcher.openGalleryWithPermissionCheck(this);
        });
        ibAttach.setOnLongClickListener(view -> {
            TweetActivityPermissionsDispatcher.pickLatestGalleryPictureWithPermissionCheck(this);
            return true;
        });

        // カメラボタン
        ibCamera = (ImageButton) findViewById(R.id.ibTweetTakePic);
        ibCamera.setOnClickListener(v -> {
            TweetActivityPermissionsDispatcher.launchCameraWithPermissionCheck(this);
        });

        // ハッシュタグ入力ボタン
        ImageButton ibHash = (ImageButton) findViewById(R.id.ibTweetSetHash);
        ibHash.setOnClickListener(v -> {
            SimpleListDialogFragment dialogFragment = SimpleListDialogFragment.newInstance(
                    REQUEST_DIALOG_HASH_CATEGORY, "ハッシュタグ入力", null, null, null, "入力履歴から", "タイムラインから");
            dialogFragment.show(getSupportFragmentManager(), "hashtagCategory");
        });
        ibHash.setOnLongClickListener(v -> {
            appendTextInto(" #");
            return true;
        });

        // 草ボタン
        ImageButton ibGrasses = (ImageButton) findViewById(R.id.ibTweetGrasses);
        ibGrasses.setOnClickListener(v -> {
            int start = etInput.getSelectionStart();
            int end = etInput.getSelectionEnd();
            if (start < 0 || end < 0 || start == end) {
                appendTextInto("ｗ");
            } else {
                String text = etInput.getText().toString().substring(Math.min(start, end), Math.max(start, end));
                StringBuilder newText = new StringBuilder();

                BreakIterator breakIterator = BreakIterator.getCharacterInstance();
                breakIterator.setText(text);

                int graphemeStart = breakIterator.first();
                int graphemeEnd = breakIterator.next();

                while (graphemeEnd != BreakIterator.DONE) {
                    newText.append(text, graphemeStart, graphemeEnd);

                    graphemeStart = graphemeEnd;
                    graphemeEnd = breakIterator.next();

                    if (graphemeEnd != BreakIterator.DONE) {
                        newText.append("ｗ");
                    }
                }

                etInput.getText().replace(Math.min(start, end), Math.max(start, end), newText.toString());
            }
        });

        // 三点リーダボタン
        ImageButton ibSanten = (ImageButton) findViewById(R.id.ibTweetSanten);
        ibSanten.setOnClickListener(v -> appendTextInto("…"));

        // 下書きメニュー
        ImageButton ibDraft = (ImageButton) findViewById(R.id.ibTweetDraft);
        ibDraft.setOnClickListener(v -> {
            SimpleListDialogFragment dialogFragment = SimpleListDialogFragment.newInstance(
                    REQUEST_DIALOG_DRAFT_MENU, "下書きメニュー", null, null, null, "下書きを保存", "下書きを開く", "入力欄をクリア");
            dialogFragment.show(getSupportFragmentManager(), "draftMenu");
        });
    }

    /**
     * プラグインエリアの初期化
     */
    private void initializeEditPlugins() {
        // 可視性設定
        ibVisibility = (ImageButton) findViewById(R.id.ibTweetVisibility);
        ibVisibility.setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressLint("RestrictedApi")
            public void onClick(View v) {
                PopupMenu menu = new PopupMenu(TweetActivity.this, v);

                MenuItem publicItem = menu.getMenu().add(Menu.NONE, StatusDraft.Visibility.PUBLIC.ordinal(), Menu.NONE, "公開");
                if (usingDarkTheme) {
                    publicItem.setIcon(R.drawable.ic_visibility_public_light);
                } else {
                    publicItem.setIcon(R.drawable.ic_visibility_public_dark);
                }

                MenuItem unlistedItem = menu.getMenu().add(Menu.NONE, StatusDraft.Visibility.UNLISTED.ordinal(), Menu.NONE, "未収載");
                if (usingDarkTheme) {
                    unlistedItem.setIcon(R.drawable.ic_visibility_unlisted_light);
                } else {
                    unlistedItem.setIcon(R.drawable.ic_visibility_unlisted_dark);
                }

                MenuItem privateItem = menu.getMenu().add(Menu.NONE, StatusDraft.Visibility.PRIVATE.ordinal(), Menu.NONE, "非公開");
                if (usingDarkTheme) {
                    privateItem.setIcon(R.drawable.ic_visibility_private_light);
                } else {
                    privateItem.setIcon(R.drawable.ic_visibility_private_dark);
                }

                MenuItem directItem = menu.getMenu().add(Menu.NONE, StatusDraft.Visibility.DIRECT.ordinal(), Menu.NONE, "ダイレクト");
                if (usingDarkTheme) {
                    directItem.setIcon(R.drawable.ic_visibility_direct_light);
                } else {
                    directItem.setIcon(R.drawable.ic_visibility_direct_dark);
                }

                menu.setOnMenuItemClickListener(item -> {
                    TweetActivity.this.setVisibility(item.getItemId());
                    return true;
                });

                MenuPopupHelper helper = new MenuPopupHelper(TweetActivity.this, (MenuBuilder) menu.getMenu(), v);
                helper.setForceShowIcon(true);
                helper.show();
            }
        });
        setVisibility(visibility.ordinal());

        // Content Warning
        ibSpoiler = findViewById(R.id.ibTweetSpoiler);
        ibSpoiler.setOnClickListener(v -> {
            if (etSpoiler.getVisibility() == View.VISIBLE) {
                etSpoiler.setText("");
                etSpoiler.setVisibility(View.GONE);
                etInput.requestFocus();
            } else {
                String defaultText = sp.getString("pref_toot_cw_default_text", "");
                if (!TextUtils.isEmpty(defaultText)) {
                    etSpoiler.setText(defaultText);
                    etSpoiler.selectAll();
                }
                etSpoiler.setVisibility(View.VISIBLE);
                etSpoiler.requestFocus();
            }
        });

        // スクリーンネーム入力支援
        ibSNPicker = (ImageButton) findViewById(R.id.ibTweetSNPicker);
        ibSNPicker.setOnClickListener(v -> {
            Intent intent = new Intent(TweetActivity.this, SNPickerActivity.class);
            startActivityForResult(intent, REQUEST_SNPICKER);
        });

        // 音声入力
        ImageButton ibVoice = (ImageButton) findViewById(R.id.ibTweetVoiceInput);
        ibVoice.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "ツイートする内容をお話しください");
            try {
                startActivityForResult(intent, REQUEST_VOICE);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(TweetActivity.this, "音声入力が組み込まれていません", Toast.LENGTH_SHORT).show();
            }
        });

        // モールス入力
        ImageButton ibMorse = (ImageButton) findViewById(R.id.ibTweetMorseInput);
        ibMorse.setOnClickListener(v -> {
            Intent intent = new Intent(TweetActivity.this, MorseInputActivity.class);
            startActivityForResult(intent, REQUEST_NOWPLAYING);
        });

        // これ聴いてるんだからねっ！連携
        final PackageManager pm = getPackageManager();
        ImageButton ibNowPlay = (ImageButton) findViewById(R.id.ibTweetNowPlaying);
        try {
            final ApplicationInfo ai = pm.getApplicationInfo("biz.Fairysoft.KoreKiiteru", 0);
            ibNowPlay.setVisibility(View.VISIBLE);
            ibNowPlay.setOnClickListener(v -> {
                Intent intent = new Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT");
                intent.addCategory("com.adamrocker.android.simeji.REPLACE");
                intent.setPackage(ai.packageName);
                startActivityForResult(intent, REQUEST_NOWPLAYING);
            });
        } catch (PackageManager.NameNotFoundException ignored) {}

        // twiccaプラグインのロード
        Intent query = new Intent("jp.r246.twicca.ACTION_EDIT_TWEET");
        query.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> plugins = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
        Collections.sort(plugins, new ResolveInfo.DisplayNameComparator(pm));
        llTweetExtra = (LinearLayout) findViewById(R.id.llTweetExtra);
        final int iconSize = (int) (getResources().getDisplayMetrics().density * PLUGIN_ICON_DIP);
        for (ResolveInfo ri : plugins) {
            ImageButton imageButton = new AppCompatImageButton(this);
            Bitmap sourceIcon = ((BitmapDrawable) ri.activityInfo.loadIcon(pm)).getBitmap();
            imageButton.setImageBitmap(Bitmap.createScaledBitmap(sourceIcon, iconSize, iconSize, true));
            imageButton.setTag(ri);
            imageButton.setOnClickListener(v -> {
                ResolveInfo ri1 = (ResolveInfo) v.getTag();

                Intent intent = new Intent("jp.r246.twicca.ACTION_EDIT_TWEET");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setPackage(ri1.activityInfo.packageName);
                intent.setClassName(ri1.activityInfo.packageName, ri1.activityInfo.name);

                String text = etInput.getText().toString();
                intent.putExtra(Intent.EXTRA_TEXT, text);

                TwiccaParameterHelper paramHelper = new TwiccaParameterHelper(text);
                intent.putExtra("prefix", paramHelper.prefix);
                intent.putExtra("suffix", paramHelper.suffix);
                intent.putExtra("user_input", paramHelper.userInput);

                if (status != null) {
                    intent.putExtra("in_reply_to_status_id", status.getId());
                }
                intent.putExtra("cursor", etInput.getSelectionStart());

                startActivityForResult(intent, REQUEST_TWICCA_PLUGIN);
            });
            imageButton.setOnLongClickListener(v -> {
                ResolveInfo info = (ResolveInfo) v.getTag();
                Toast toast = Toast.makeText(TweetActivity.this, info.activityInfo.loadLabel(pm), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, -128);
                toast.show();
                return true;
            });
            llTweetExtra.addView(imageButton, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void restoreState(Bundle state) {
        isDirectMessage = state.getBoolean("isDirectMessage");
        directMessageDestId = state.getLong("directMessageDestId");
        directMessageDestSN = state.getString("directMessageDestSN");
        isComposerMode = state.getBoolean("isComposerMode");
        writers = (ArrayList<AuthUserRecord>) state.getSerializable("writers");
        useStoredWriters = state.getBoolean("useStoredWriters");
        cameraTemp = state.getParcelable("cameraTemp");
        initialDraft = state.getParcelable("initialDraft");

        Stream.of(state.<Uri>getParcelableArrayList("attachPictureUris")).forEach(this::attachPicture);

        updateWritersView();
        setVisibility(state.getInt("visibility"));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isDirectMessage", isDirectMessage);
        outState.putLong("directMessageDestId", directMessageDestId);
        outState.putString("directMessageDestSN", directMessageDestSN);
        outState.putBoolean("isComposerMode", isComposerMode);
        outState.putSerializable("writers", writers);
        outState.putBoolean("useStoredWriters", useStoredWriters);
        outState.putParcelable("cameraTemp", cameraTemp);
        outState.putParcelable("initialDraft", initialDraft);
        ArrayList<Uri> attachPictureUris = new ArrayList<>();
        for (AttachPicture picture : attachPictures) {
            attachPictureUris.add(picture.uri);
        }
        outState.putParcelableArrayList("attachPictureUris", attachPictureUris);
        outState.putInt("visibility", visibility.ordinal());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (TextUtils.isEmpty(etSpoiler.getText())) {
            etSpoiler.setVisibility(View.GONE);
        } else {
            etSpoiler.setVisibility(View.VISIBLE);
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    void openGallery() {
        //添付上限判定
        if (maxMediaPerUpload > 1 && attachPictures.size() >= maxMediaPerUpload) {
            Toast.makeText(TweetActivity.this, "これ以上画像を添付できません。", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(TweetActivity.this, MultiPickerActivity.class);
        intent.putExtra(MultiPickerActivity.EXTRA_PICK_LIMIT, maxMediaPerUpload - attachPictures.size());
        intent.putExtra(MultiPickerActivity.EXTRA_THEME, ThemeUtil.getActivityThemeId(getApplicationContext()));
        intent.putExtra(MultiPickerActivity.EXTRA_CLOSE_ENTER_ANIMATION, R.anim.activity_tweet_close_enter);
        intent.putExtra(MultiPickerActivity.EXTRA_CLOSE_EXIT_ANIMATION, R.anim.activity_tweet_close_exit);
        startActivityForResult(intent, REQUEST_GALLERY);
        overridePendingTransition(R.anim.activity_tweet_open_enter, R.anim.activity_tweet_open_exit);
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    void pickLatestGalleryPicture() {
        if (attachPictures.size() == 1 || (attachPictures.size() < maxMediaPerUpload)) {
            Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
            if (c != null) {
                if (c.moveToLast()) {
                    long id = c.getLong(c.getColumnIndex(MediaStore.Images.Media._ID));
                    attachPicture(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                    Toast.makeText(TweetActivity.this, "最後に撮影した画像を添付します", Toast.LENGTH_LONG).show();
                }
                c.close();
            }
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    void onDeniedReadExternalStorage() {
        if (PermissionUtils.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Toast.makeText(TweetActivity.this, "ギャラリーにアクセスできません。", Toast.LENGTH_SHORT).show();
        } else {
            currentDialog = new AlertDialog.Builder(this)
                    .setTitle("許可が必要")
                    .setMessage("画像を添付するには、手動で設定画面からストレージへのアクセスを許可する必要があります。")
                    .setPositiveButton("設定画面へ", (dialog, which) -> {
                        currentDialog = null;

                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    })
                    .setNegativeButton("今はしない", (dialog, which) -> {
                        currentDialog = null;
                    })
                    .create();
            currentDialog.show();
        }
    }

    @OnShowRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
    void showRationaleForReadExternalStorage(final PermissionRequest request) {
        currentDialog = new AlertDialog.Builder(this)
                .setTitle("許可が必要")
                .setMessage("画像を添付するためには、ストレージへのアクセス許可が必要です。")
                .setPositiveButton("許可", (dialog, which) -> {
                    currentDialog = null;
                    request.proceed();
                })
                .setNegativeButton("許可しない", (dialog, which) -> {
                    currentDialog = null;
                    request.cancel();
                })
                .create();
        currentDialog.show();
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void launchCamera() {
        //添付上限判定
        if (maxMediaPerUpload > 1 && attachPictures.size() >= maxMediaPerUpload) {
            Toast.makeText(TweetActivity.this, "これ以上画像を添付できません。", Toast.LENGTH_SHORT).show();
            return;
        }
        //SDカード使用可否のチェックを行う
        boolean existExternal = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (!existExternal) {
            Toast.makeText(TweetActivity.this, "ストレージが使用できないため、カメラを起動できません", Toast.LENGTH_SHORT).show();
            return;
        }
        //保存先パスを作成する
        String extStorage = Environment.getExternalStorageDirectory().getPath();
        File extDestDir = new File(extStorage + "/DCIM/" + getPackageName());
        if (!extDestDir.exists()) {
            extDestDir.mkdirs();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = sdf.format(new Date(System.currentTimeMillis()));
        File destFile = new File(extDestDir.getPath() + "/" + fileName + ".jpg");
        //コンテントプロバイダに登録
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATA, destFile.getPath());
        cameraTemp = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        //カメラを呼び出す
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraTemp);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void onDeniedWriteExternalStorage() {
        if (PermissionUtils.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Toast.makeText(TweetActivity.this, "カメラを起動できません。", Toast.LENGTH_SHORT).show();
        } else {
            currentDialog = new AlertDialog.Builder(this)
                    .setTitle("許可が必要")
                    .setMessage("カメラを使用するには、手動で設定画面からストレージへのアクセスを許可する必要があります。")
                    .setPositiveButton("設定画面へ", (dialog, which) -> {
                        currentDialog = null;

                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    })
                    .setNegativeButton("今はしない", (dialog, which) -> {
                        currentDialog = null;
                    })
                    .create();
            currentDialog.show();
        }
    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showRationaleForWriteExternalStorage(final PermissionRequest request) {
        currentDialog = new AlertDialog.Builder(this)
                .setTitle("許可が必要")
                .setMessage("カメラを起動するためには、ストレージへのアクセス許可が必要です。")
                .setPositiveButton("許可", (dialog, which) -> {
                    currentDialog = null;
                    request.proceed();
                })
                .setNegativeButton("許可しない", (dialog, which) -> {
                    currentDialog = null;
                    request.cancel();
                })
                .create();
        currentDialog.show();
    }

    private void postTweet() {
        if (TextUtils.isEmpty(etInput.getText()) && attachPictures.isEmpty()) {
            Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
            return;
        } else if (!isValidInputLength()) {
            Toast.makeText(TweetActivity.this, "ツイート上限文字数を超えています", Toast.LENGTH_SHORT).show();
            return;
        }
        if (writers.size() < 1) {
            Toast.makeText(TweetActivity.this, "アカウントを指定してください", Toast.LENGTH_SHORT).show();
            return;
        }

        //エイリアス処理
        String inputText = etInput.getText().toString();
        String preprocessedInput = TweetPreprocessor.preprocess(new TweetPreprocessor.TweetPreprocessorDepends() {
            @Override
            public TweetActivity getActivity() {
                return TweetActivity.this;
            }

            @Override
            public List<AuthUserRecord> getWriters() {
                return writers;
            }

            @Override
            public String getBatteryTweetText() {
                Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int percent = intent.getIntExtra("level", 0) * 100 / intent.getIntExtra("scale", -1);
                boolean charging = intent.getIntExtra("plugged", 0) > 0;
                return String.format("%s のバッテリー残量: %s%d%%", Build.MODEL, charging ? "🔌" : "🔋", percent);
            }
        }, inputText);

        if (preprocessedInput == null) {
            return;
        } else {
            inputText = preprocessedInput;
        }

        //ドラフトを作成
        StatusDraft draft = getTweetDraft();
        draft.setText(inputText);

        //使用されているハッシュタグを記憶
        List<String> hashtags = EXTRACTOR.extractHashtags(inputText);
        for (String hashtag : hashtags) {
            usedHashes.put(hashtag);
        }
        usedHashes.save(getApplicationContext());

        if (isComposerMode) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DRAFT, draft);
            setResult(RESULT_OK, intent);
        } else {
            //サービスに投げる
            Intent intent = PostService.newIntent(TweetActivity.this, draft);
            ContextCompat.startForegroundService(this, intent);

            if (sp.getBoolean("first_guide", true)) {
                sp.edit().putBoolean("first_guide", false).commit();
            }

            setResult(RESULT_OK);
        }
        finish();
    }

    private void updateWritersView() {
        boolean showMastodonFunctions = false;

        StringBuilder sb = new StringBuilder();
        if (writers.size() < 1) {
            sb.append(">> SELECT ACCOUNT(S)");
        } else for (int i = 0; i < writers.size(); ++i) {
            AuthUserRecord writer = writers.get(i);

            if (i > 0) sb.append("\n");
            sb.append("@");
            sb.append(writer.ScreenName);

            if (writer.Provider.getApiType() == Provider.API_MASTODON) {
                showMastodonFunctions = true;
            }
        }
        tvTweetBy.setText(sb.toString());

        if (showMastodonFunctions) {
            ibVisibility.setVisibility(View.VISIBLE);
            ibSpoiler.setVisibility(View.VISIBLE);
            cbSensitive.setVisibility(View.VISIBLE);
        } else {
            ibVisibility.setVisibility(View.GONE);
            ibSpoiler.setVisibility(View.GONE);
            cbSensitive.setVisibility(View.GONE);
        }
    }

    private void showQuotedStatus() {
        if (status != null) {
            Status s = status.getOriginStatus();
            tvTitle.setText(String.format("Reply >> @%s: %s", s.getUser().getScreenName(), s.getText()));
        } else {
            tvTitle.setText("Reply >> load failed. (deleted or temporary error)");
        }
    }

    private void updatePostValidator() {
        if (!isTwitterServiceBound() || getTwitterService() == null) {
            return;
        }

        validators.clear();
        TwitterService service = getTwitterService();
        for (AuthUserRecord writer : writers) {
            ProviderApi api = service.getProviderApi(writer);
            if (api != null) {
                validators.add(api.getPostValidator(writer));
            }
        }

        updateTweetCount();
    }

    private Map<String, Object> getPostValidatorOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(TweetValidator.OPTION_IS_DIRECT_MESSAGE, isDirectMessage);
        options.put(TweetValidator.OPTION_INCLUDE_QUOTE_URL, attachPictures.isEmpty() && PATTERN_QUOTE.matcher(etInput.getText().toString()).find());
        return options;
    }

    private void updateTweetCount() {
        if (validators.isEmpty()) {
            tvCount.setText("---");
            tvCount.setTextColor(tweetCountColor);
            return;
        }

        String inputText = etInput.getText().toString();
        Map<String, Object> options = getPostValidatorOptions();
        int remainCount = Integer.MAX_VALUE;
        for (PostValidator validator : validators) {
            int remain = validator.getMaxLength(options) - validator.getMeasuredLength(inputText, options);
            if (remain < remainCount) {
                remainCount = remain;
            }
        }

        tvCount.setText(String.valueOf(remainCount));
        if (remainCount < 0) {
            tvCount.setTextColor(tweetCountOverColor);
        } else {
            tvCount.setTextColor(tweetCountColor);
        }
    }

    private boolean isValidInputLength() {
        String inputText = etInput.getText().toString();
        Map<String, Object> options = getPostValidatorOptions();
        for (PostValidator validator : validators) {
            if (validator.getMaxLength(options) < validator.getMeasuredLength(inputText, options)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_GALLERY:
                    for (Parcelable uri : data.getParcelableArrayExtra(MultiPickerActivity.EXTRA_URIS)) {
                        attachPicture((Uri) uri);
                    }
                    break;
                case REQUEST_CAMERA: {
                    if (data != null && data.getData() != null) {
                        //getDataでUriが返ってくる端末用
                        //フィールドは手に入ったUriで上書き
                        cameraTemp = data.getData();
                    }
                    if (cameraTemp == null) {
                        Toast.makeText(TweetActivity.this, "カメラとの連携に失敗しました。\n使用したカメラアプリとの相性かもしれません。", Toast.LENGTH_LONG).show();
                        return;
                    }
                    //添付に追加する
                    attachPicture(cameraTemp);
                    cameraTemp = null;
                    break;
                }
                case REQUEST_VOICE: {
                    List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results.size() > 0) {
                        appendTextInto(results.get(0));
                    } else {
                        Toast.makeText(TweetActivity.this, "認識に失敗しました", Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case REQUEST_NOWPLAYING:
                case REQUEST_TOTSUSI:
                    appendTextInto(data.getStringExtra("replace_key"));
                    break;
                case REQUEST_SNPICKER:
                    appendTextInto(data.getStringExtra(SNPickerActivity.EXTRA_SCREEN_NAME) + " ");
                    break;
                case REQUEST_TWICCA_PLUGIN:
                    etInput.setText(data.getStringExtra(Intent.EXTRA_TEXT));
                    etInput.setSelection(data.getIntExtra("cursor", etInput.getSelectionStart()));
                    break;
                case REQUEST_ACCOUTS: {
                    writers = (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                    updateWritersView();
                    updatePostValidator();
                    break;
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            switch (requestCode) {
                case REQUEST_CAMERA: {
                    try {
                        ContentResolver resolver = getContentResolver();
                        Cursor c = resolver.query(cameraTemp, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                        if (c != null) {
                            if (c.moveToFirst()) {
                                resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        MediaStore.Images.Media.DATA + "=?",
                                        new String[]{c.getString(0)});
                            }
                            c.close();
                        }
                    } catch (NullPointerException e) {
                        Toast.makeText(getApplicationContext(),
                                "カメラとの連携をキャンセルする際、一時ファイルの削除に失敗しました。\nもしギャラリーにゴミが増えていたら始末をお願いします。",
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        TweetActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_INIT_ATTACH:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ArrayList<String> mediaUri = getIntent().getStringArrayListExtra(EXTRA_MEDIA);
                    for (String s : mediaUri) {
                        attachPicture(Uri.parse(s));
                    }
                    initialDraft = getTweetDraft().copyForJava();
                } else {
                    Toast.makeText(this, "添付画像の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (useStoredWriters && isTwitterServiceBound() && getTwitterService() != null) {
            getTwitterService().setWriterUsers(writers);
        }
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_save_tags", false)) {
            List<String> strings = EXTRACTOR.extractHashtags(etInput.getText().toString());
            String json = new Gson().toJson(strings);
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .edit()
                    .putString("pref_saved_tags", json)
                    .commit();
        }
        // Pluggaloidプラグインの解放
        if (isLoadedPluggaloid) {
            int count = llTweetExtra.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = llTweetExtra.getChildAt(i);
                if (child != null) {
                    Object tag = child.getTag();
                    if (tag instanceof ProcWrapper) {
                        ((ProcWrapper) tag).dispose();
                        llTweetExtra.removeViewAt(i--);
                        --count;
                    }
                }
            }
            isLoadedPluggaloid = false;
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (sp.getBoolean("pref_dialog_cancel_post", true) && !getTweetDraft().equals(initialDraft)) {
            SimpleAlertDialogFragment dialogFragment =
                    SimpleAlertDialogFragment.newInstance(REQUEST_DIALOG_BACK, "確認", "下書きに保存しますか?", "保存", "破棄", "キャンセル");
            dialogFragment.show(getSupportFragmentManager(), "");
        } else {
            super.onBackPressed();
        }
    }

    private ImageView createAttachThumb(Bitmap bmp) {
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, getResources().getDisplayMetrics());
        final ImageView ivAttach = new ImageView(this);
        ivAttach.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        ivAttach.setImageBitmap(bmp);
        ivAttach.setOnClickListener(v -> {
            AlertDialog ad = new AlertDialog.Builder(TweetActivity.this)
                    .setTitle("添付の取り消し")
                    .setMessage("画像の添付を取り消してもよろしいですか？")
                    .setPositiveButton("はい", (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;

                        llTweetAttachInner.removeView(ivAttach);
                        for (Iterator<AttachPicture> it = attachPictures.iterator(); it.hasNext(); ) {
                            AttachPicture attachPicture = it.next();
                            if (attachPicture.imageView == ivAttach) {
                                it.remove();
                                break;
                            }
                        }

                        if (attachPictures.isEmpty()) {
                            llTweetAttachParent.setVisibility(View.GONE);
                        }

                        updateTweetCount();
                    })
                    .setNegativeButton("いいえ", (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;
                    })
                    .setOnCancelListener(dialog -> {
                        dialog.dismiss();
                        currentDialog = null;
                    })
                    .create();
            ad.show();
            currentDialog = ad;
        });
        return ivAttach;
    }

    private void attachPicture(Uri uri) {
        // file:// か content://media/ 以外はきちんと扱えるか信用ならないのでコピーを取り、そちらを使うようにする
        if (!"file".equals(uri.getScheme()) && !("content".equals(uri.getScheme()) && "media".equals(uri.getHost()))) {
            InputStream input = null;
            OutputStream output = null;
            try {
                File attachesDir = new File(getExternalFilesDir(null), "attaches");
                if (!attachesDir.exists()) {
                    attachesDir.mkdirs();
                }
                File outputFile = new File(attachesDir, UUID.randomUUID().toString());

                input = getContentResolver().openInputStream(uri);
                output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer, 0, buffer.length)) != -1) {
                    output.write(buffer, 0, read);
                }

                uri = Uri.fromFile(outputFile);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "画像添付エラー", Toast.LENGTH_SHORT).show();
                return;
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignore) {}
                }
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException ignore) {}
                }
            }
        }

        final AttachPicture pic = new AttachPicture();
        pic.uri = uri;
        try {
            int[] size = new int[2];
            Bitmap bmp = BitmapUtil.resizeBitmap(this, uri, 256, 256, size);
            pic.width = size[0];
            pic.height = size[1];
            pic.imageView = createAttachThumb(bmp);
            pic.imageView.setOnLongClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, pic.uri, getApplicationContext(), PreviewActivity.class));
                return true;
            });
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "画像添付エラー", Toast.LENGTH_SHORT).show();
            return;
        }
        if (maxMediaPerUpload == 1 && attachPictures.size() >= 1) {
            llTweetAttachInner.removeView(attachPictures.get(0).imageView);
            attachPictures.set(0, pic);
        } else {
            attachPictures.add(pic);
        }
        llTweetAttachInner.addView(pic.imageView);
        llTweetAttachParent.setVisibility(View.VISIBLE);

        updateTweetCount();
    }

    private void appendTextInto(String text) {
        int start = etInput.getSelectionStart();
        int end = etInput.getSelectionEnd();
        etInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }

    @Override
    public void onDraftSelected(StatusDraft selected) {
        Intent intent = selected.getTweetIntent(this);

        startActivity(intent);
        finish();
    }

    private StatusDraft getTweetDraft() {
        StatusDraft draft = getIntent().getParcelableExtra(EXTRA_DRAFT);
        if (isDirectMessage) {
            if (draft == null) {
                draft = new StatusDraft(
                        writers,
                        etInput.getText().toString(),
                        System.currentTimeMillis(),
                        String.valueOf(directMessageDestId),
                        false,
                        AttachPicture.toUriList(attachPictures),
                        false,
                        0,
                        0,
                        cbSensitive.isChecked(),
                        true,
                        false,
                        directMessageDestSN,
                        StatusDraft.Visibility.DIRECT,
                        null);
            } else {
                draft.setWriters(writers);
                draft.setText(etInput.getText().toString());
                draft.setInReplyTo(String.valueOf(directMessageDestId));
                draft.setQuoted(false);
                draft.setAttachPictures(AttachPicture.toUriList(attachPictures));
                draft.setUseGeoLocation(false);
                draft.setGeoLatitude(0);
                draft.setGeoLongitude(0);
                draft.setPossiblySensitive(cbSensitive.isChecked());
                draft.setDirectMessage(true);
                draft.setMessageTarget(directMessageDestSN);
                draft.setVisibility(StatusDraft.Visibility.DIRECT);
                draft.setSpoilerText(null);
            }
        } else if (draft == null) {
            draft = new StatusDraft(
                    writers,
                    etInput.getText().toString(),
                    System.currentTimeMillis(),
                    (status == null) ? null : status.getUrl(),
                    false,
                    AttachPicture.toUriList(attachPictures),
                    false,
                    0,
                    0,
                    cbSensitive.isChecked(),
                    false,
                    false,
                    null,
                    visibility,
                    etSpoiler.getText().toString());
        } else {
            draft.setWriters(writers);
            draft.setText(etInput.getText().toString());
            draft.setInReplyTo((status == null) ? null : status.getUrl());
            draft.setQuoted(false);
            draft.setAttachPictures(AttachPicture.toUriList(attachPictures));
            draft.setUseGeoLocation(false);
            draft.setGeoLatitude(0);
            draft.setGeoLongitude(0);
            draft.setPossiblySensitive(cbSensitive.isChecked());
            draft.setDirectMessage(false);
            draft.setMessageTarget(null);
            draft.setVisibility(visibility);
            draft.setSpoilerText(etSpoiler.getText().toString());
        }
        setIntent(getIntent().putExtra(EXTRA_DRAFT, draft));
        return draft;
    }

    private void setVisibility(int visibility) {
        switch (visibility) {
            case 0:
                this.visibility = StatusDraft.Visibility.PUBLIC;
                if (usingDarkTheme) {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_public_light);
                } else {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_public_dark);
                }
                break;
            case 1:
                this.visibility = StatusDraft.Visibility.UNLISTED;
                if (usingDarkTheme) {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_unlisted_light);
                } else {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_unlisted_dark);
                }
                break;
            case 2:
                this.visibility = StatusDraft.Visibility.PRIVATE;
                if (usingDarkTheme) {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_private_light);
                } else {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_private_dark);
                }
                break;
            case 3:
                this.visibility = StatusDraft.Visibility.DIRECT;
                if (usingDarkTheme) {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_direct_light);
                } else {
                    ibVisibility.setImageResource(R.drawable.ic_visibility_direct_dark);
                }
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        if (useStoredWriters && writers.size() == 0) {
            writers = getTwitterService().getWriterUsers();
            updateWritersView();
            initialDraft.setWriters(writers);
        }
        updatePostValidator();

        TwitterAPIConfiguration apiConfiguration = ((TwitterApi) getTwitterService().getProviderApi(Provider.API_TWITTER)).getApiConfiguration();
        if (apiConfiguration != null) {
            maxMediaPerUpload = 4;//apiConfiguration.getMaxMediaPerUpload();
            ((TextView) findViewById(R.id.tvTweetAttach)).setText("Attach (max:" + maxMediaPerUpload + ")");
            shortUrlLength = apiConfiguration.getShortURLLength();
        }

        //Pluggaloidプラグインのバインド
        if (getTwitterService().getmRuby() != null && !isLoadedPluggaloid) {
            Object[] result = Plugin.filtering(getTwitterService().getmRuby(), "twicca_action_edit_tweet", new HashMap());
            if (result != null && result[0] instanceof Map) {
                Stream.of(((Map) result[0]).values()).filter(o -> o instanceof Map).forEach(o -> {
                    Map entry = (Map) o;
                    final String slug = Optional.ofNullable((String) entry.get("slug")).orElse("missing_slug");
                    final String label = Optional.ofNullable((String) entry.get("label")).orElse(slug);
                    final ProcWrapper exec = (ProcWrapper) entry.get("exec");
                    final int iconSize = (int) (getResources().getDisplayMetrics().density * PLUGIN_ICON_DIP);
                    {
                        ImageButton imageButton = new AppCompatImageButton(this);
                        Bitmap sourceIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_tweet);
                        imageButton.setImageBitmap(Bitmap.createScaledBitmap(sourceIcon, iconSize, iconSize, true));
                        imageButton.setTag(exec);
                        imageButton.setOnClickListener(v -> {
                            Map<String, String> extra = new LinkedHashMap<>();

                            String text = etInput.getText().toString();
                            extra.put("text", text);

                            TwiccaParameterHelper paramHelper = new TwiccaParameterHelper(text);
                            extra.put("prefix", paramHelper.prefix);
                            extra.put("suffix", paramHelper.suffix);
                            extra.put("user_input", paramHelper.userInput);

                            if (status != null) {
                                extra.put("in_reply_to_status_id", String.valueOf(status.getId()));
                            }
                            extra.put("cursor", String.valueOf(etInput.getSelectionStart()));

                            if (exec != null) {
                                try {
                                    Object r = exec.exec(extra);
                                    if (r instanceof Map) {
                                        Map rm = (Map) r;
                                        String resultCode = (String) rm.get("result_code");
                                        Map intent = (Map) rm.get("intent");
                                        if ("ok".equals(resultCode) && intent != null) {
                                            String t = (String) intent.get("text");
                                            Integer cursor = (Integer) intent.get("cursor");
                                            if (t != null) {
                                                etInput.setText(t);
                                            }
                                            if (cursor != null) {
                                                etInput.setSelection(cursor);
                                            }
                                        }
                                    }
                                } catch (MRubyException e) {
                                    e.printStackTrace();
                                    Toast.makeText(getApplicationContext(),
                                            String.format("Procの実行中にMRuby上で例外が発生しました\n%s", e.getMessage()),
                                            Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        String.format("Procの実行に失敗しました\ntwicca_action :%s の宣言で適切なブロックを渡していますか？", slug),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                        imageButton.setOnLongClickListener(v -> {
                            Toast toast = Toast.makeText(TweetActivity.this, label, Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, -128);
                            toast.show();
                            return true;
                        });
                        llTweetExtra.addView(imageButton, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    }
                });
                isLoadedPluggaloid = true;
            }
        }
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public void onDialogChose(int requestCode, int which, Bundle extras) {
        switch (requestCode) {
            case REQUEST_DIALOG_CLEAR:
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    etInput.setText("");
                }
                break;
            case REQUEST_DIALOG_YUKARIN:
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    etInput.setText("＼ﾕｯｶﾘｰﾝ／");
                    btnPost.performClick();
                }
                break;
            case REQUEST_DIALOG_POST:
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    postTweet();
                }
                break;
            case REQUEST_DIALOG_BACK:
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        getTwitterService().getDatabase().updateDraft(getTweetDraft());
                    case DialogInterface.BUTTON_NEUTRAL:
                        finish();
                        break;
                }
                break;
        }
    }

    @Override
    public void onDialogChose(int requestCode, int which, String value, Bundle extras) {
        switch (requestCode) {
            case REQUEST_DIALOG_TEMPLATE:
                if (value != null) {
                    etInput.setText(value);
                }
                break;
            case REQUEST_DIALOG_HASH_CATEGORY:
                if (which > -1) {
                    SimpleListDialogFragment dialogFragment;
                    switch (which) {
                        case 0:
                            dialogFragment = SimpleListDialogFragment.newInstance(
                                    REQUEST_DIALOG_HASH_VALUE, "ハッシュタグ入力履歴", null, null, "キャンセル", usedHashes.getAll(), null);
                            break;
                        case 1:
                            dialogFragment = SimpleListDialogFragment.newInstance(
                                    REQUEST_DIALOG_HASH_VALUE, "TLで見かけたハッシュタグ", null, null, "キャンセル", getTwitterService().getHashCache().getAll(), null);
                            break;
                        default:
                            return;
                    }
                    dialogFragment.show(getSupportFragmentManager(), "hashtagValue");
                }
                break;
            case REQUEST_DIALOG_HASH_VALUE:
                if (value != null) {
                    etInput.getText().append(" ").append(value);
                }
                break;
            case REQUEST_DIALOG_DRAFT_MENU:
                switch (which) {
                    case 0: {
                        if (TextUtils.isEmpty(etInput.getText()) && attachPictures.isEmpty()) {
                            Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
                        } else {
                            getTwitterService().getDatabase().updateDraft(getTweetDraft());
                            Toast.makeText(TweetActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
                    case 1: {
                        DraftDialogFragment draftDialogFragment = new DraftDialogFragment();
                        draftDialogFragment.show(getSupportFragmentManager(), "draftDialog");
                        break;
                    }
                    case 2: {
                        SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                                REQUEST_DIALOG_CLEAR,
                                "確認",
                                "入力中の文章をすべて削除してもよろしいですか？",
                                "OK",
                                "キャンセル");
                        dialogFragment.show(getSupportFragmentManager(), "dialog");
                        break;
                    }
                }
                break;
        }
    }

    private static class AttachPicture {
        public Uri uri = null;
        public int width = -1;
        public int height = -1;
        public ImageView imageView;

        public static ArrayList<Uri> toUriList(List<AttachPicture> from) {
            ArrayList<Uri> list = new ArrayList<>();
            for (AttachPicture ap : from) {
                list.add(ap.uri);
            }
            return list;
        }
    }

    private static class TwiccaParameterHelper {
        public String prefix;
        public String suffix;
        public String userInput;

        TwiccaParameterHelper(String text) {
            Matcher prefixMatcher = PATTERN_PREFIX.matcher(text);
            if (prefixMatcher.find()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < prefixMatcher.groupCount(); i++) {
                    sb.append(prefixMatcher.group(i + 1));
                }
                prefix = sb.toString();
            }

            Matcher suffixMatcher = PATTERN_SUFFIX.matcher(text);
            if (suffixMatcher.find() && suffixMatcher.groupCount() > 0) {
                suffix = suffixMatcher.group(1);
            }

            Pattern userInputPattern = Pattern.compile(prefix + "(.+)" + suffix);
            Matcher userInputMatcher = userInputPattern.matcher(text);
            if (userInputMatcher.find() && userInputMatcher.groupCount() > 0) {
                userInput = userInputMatcher.group(1);
            } else {
                userInput = "";
            }
        }
    }

    /**
     * 状態を保存する必要があるかメモするためだけの存在。正直いらねえ。
     */
    @Retention(RetentionPolicy.SOURCE)
    private @interface NeedSaveState {}
}
