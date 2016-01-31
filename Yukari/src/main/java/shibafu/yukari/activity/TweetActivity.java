package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.twitter.Extractor;
import com.twitter.Validator;
import info.shibafu528.gallerymultipicker.MultiPickerActivity;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.FragmentYukariBase;
import shibafu.yukari.common.*;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.database.Template;
import shibafu.yukari.fragment.DraftDialogFragment;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.fragment.SimpleListDialogFragment;
import shibafu.yukari.plugin.MorseInputActivity;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.AttrUtil;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.StringUtil;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterAPIConfiguration;
import twitter4j.TwitterException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TweetActivity extends FragmentYukariBase implements DraftDialogFragment.DraftDialogEventListener, SimpleAlertDialogFragment.OnDialogChoseListener, SimpleListDialogFragment.OnDialogChoseListener{

    public static final int MODE_TWEET   = 0;
    public static final int MODE_REPLY   = 1;
    public static final int MODE_DM      = 2;
    public static final int MODE_QUOTE   = 3;
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

    private static final int PLUGIN_ICON_DIP = 28;

    private static final int TWEET_COUNT_LIMIT = 140;
    private static final int TWEET_COUNT_LIMIT_DM = 10000;

    private static final Pattern PATTERN_PREFIX = Pattern.compile("(@[0-9a-zA-Z_]{1,15} )+.*");
    private static final Pattern PATTERN_SUFFIX = Pattern.compile(".*( (RT |QT |\")@[0-9a-zA-Z_]{1,15}: .+)");

    private static final Extractor EXTRACTOR = new Extractor();

    //入力欄カウント系
    private EditText etInput;
    private TextView tvCount;
    private int tweetCountLimit = TWEET_COUNT_LIMIT;
    private int tweetCount = TWEET_COUNT_LIMIT;
    private int reservedCount = 0;

    //DMフラグ
    private boolean isDirectMessage = false;
    private long directMessageDestId;
    private String directMessageDestSN;

    //編集モード
    private boolean isComposerMode = false;

    //添付プレビュー
    private LinearLayout llTweetAttachParent;
    private LinearLayout llTweetAttachInner;

    //Writer指定
    private ArrayList<AuthUserRecord> writers = new ArrayList<>();
    //アカウントのWriter指定を使用する(Writerアカウント指定呼び出しの場合は折る)
    private boolean useStoredWriters = true;

    private Status status;
    private List<AttachPicture> attachPictures = new ArrayList<>();

    private SharedPreferences sp;

    //短縮URLの文字数
    private int shortUrlLength = 22;

    //最大添付数
    private int maxMediaPerUpload = 4;

    //撮影用の一時変数
    private Uri cameraTemp;

    private AlertDialog currentDialog;

    //ScreenNameピッカーの呼び出しボタン
    private ImageButton ibSNPicker;

    //Writer一覧ビュー
    private TextView tvTweetBy;
    private Button btnPost;

    //解決済みリソース
    private int tweetCountColor;
    private int tweetCountOverColor;

    //::batt
    private String batteryTweet;
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int percent = intent.getIntExtra("level", 0) * 100 / intent.getIntExtra("scale", -1);
            boolean charging = intent.getIntExtra("plugged", 0) > 0;
            batteryTweet = String.format("%s のバッテリー残量: %s%d%%", Build.MODEL, charging ? "🔌" : "🔋", percent);
        }
    };

    //最近使ったハッシュタグ
    private UsedHashes usedHashes;

    //定型文入力 一時変数
    private List<String> templateStrings;

    //初期状態のスナップショット
    private TweetDraft initialDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String theme = sp.getString("pref_theme", "light");
        switch (theme) {
            case "light":
                setTheme(R.style.VertAnimationTheme);
                break;
            case "dark":
                setTheme(R.style.VertAnimationTheme_Dark);
                break;
        }
        super.onCreate(savedInstanceState, true);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_tweet);

        //リソースIDを解決
        tweetCountColor = getResources().getColor(AttrUtil.resolveAttribute(getTheme(), R.attr.tweetCountColor));
        tweetCountOverColor = getResources().getColor(AttrUtil.resolveAttribute(getTheme(), R.attr.tweetCountOverColor));

        //最近使ったハッシュタグのロード
        usedHashes = new UsedHashes(getApplicationContext());

        //Extraを取得
        final Intent args = getIntent();
        Uri dataArg = args.getData();

        //アカウント表示の設定
        tvTweetBy = (TextView) findViewById(R.id.tvTweetBy);
        tvTweetBy.setOnClickListener(v -> {
            Intent intent = new Intent(TweetActivity.this, AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS, writers);
            startActivityForResult(intent, REQUEST_ACCOUTS);
        });

        //ユーザー指定がある場合は表示する (EXTRA_USER, EXTRA_WRITERS)
        AuthUserRecord user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);
        ArrayList<AuthUserRecord> argWriters = (ArrayList<AuthUserRecord>) args.getSerializableExtra(EXTRA_WRITERS);
        if (argWriters != null) {
            useStoredWriters = false;
            writers = argWriters;
            updateWritersView();
        }
        else if (user != null) {
            useStoredWriters = false;
            writers.add(user);
            updateWritersView();
        }

        //statusを取得する (EXTRA_STATUS)
        status = (Status) args.getSerializableExtra(EXTRA_STATUS);

        //WebIntent判定
        boolean isWebIntent = false;
        if (dataArg != null && dataArg.getHost().equals("twitter.com")) {
            isWebIntent = true;
        }

        //IMEの表示設定
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        //テキストエリアの設定
        tvCount = (TextView) findViewById(R.id.tvTweetCount);
        tvCount.setOnLongClickListener(v -> {
            etInput.append(StringUtil.getVersionInfo(getApplicationContext()));
            return true;
        });
        etInput = (EditText) findViewById(R.id.etTweetInput);
        etInput.setTypeface(FontAsset.getInstance(this).getFont());
        etInput.setTextSize(Integer.valueOf(sp.getString("pref_font_input", "18")));
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                int count = updateTweetCount();

                if (ibSNPicker != null) {
                    if (count > 0 && etInput.getSelectionStart() > 0 &&
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
        //デフォルト文章が設定されている場合は入力しておく (EXTRA_TEXT)
        String defaultText;
        if (isWebIntent) {
            String paramInReplyTo = dataArg.getQueryParameter("in_reply_to");
            String paramText = dataArg.getQueryParameter("text").replaceAll("%0[dD]", "\r").replaceAll("%0[aA]", "\n");
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
            }
            else if (args.hasExtra(Intent.EXTRA_SUBJECT) && (!sp.getBoolean("pref_remove_screenshot_subject", false) || !args.getStringExtra(Intent.EXTRA_SUBJECT).startsWith("Screenshot ("))) {
                defaultText = args.getStringExtra(Intent.EXTRA_SUBJECT) + " " + defaultText;
            }
        } else {
            defaultText = args.getStringExtra(EXTRA_TEXT);
        }
        if (sp.getBoolean("pref_save_tags", false)) {
            List<String> tags = new Gson().fromJson(sp.getString("pref_saved_tags", "[]"), new TypeToken<List<String>>(){}.getType());
            StringBuilder sb = new StringBuilder(TextUtils.isEmpty(defaultText)? "" : defaultText);
            sb.append(" ");
            for (String tag : tags) {
                if (sb.length() > 1) {
                    sb.append(" ");
                }
                sb.append("#");
                sb.append(tag);
            }
            defaultText = sb.toString();
        }
        etInput.setText((defaultText != null)?defaultText : sp.getString("pref_tweet_footer", ""));
        int mode = args.getIntExtra(EXTRA_MODE, MODE_TWEET);
        if (mode == MODE_REPLY || mode == MODE_QUOTE) {
            if (mode == MODE_REPLY) {
                etInput.setSelection(etInput.getText().length());
            }
            final long inReplyTo = args.getLongExtra(EXTRA_IN_REPLY_TO, -1);
            if (status == null && inReplyTo > -1) {
                TextView tvTitle = (TextView) findViewById(R.id.tvTweetTitle);
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
                            status = getTwitterService().getTwitter().showStatus(inReplyTo);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        showQuotedStatus();
                    }
                }.executeParallel();
            }
            else if (status != null) {
                showQuotedStatus();
            }
        } else if (mode == MODE_COMPOSE) {
            isComposerMode = true;
            ((TextView)findViewById(R.id.tvTweetTitle)).setText("Compose");
        }

        //添付エリアの設定
        llTweetAttachParent = (LinearLayout) findViewById(R.id.llTweetAttachParent);
        llTweetAttachInner = (LinearLayout) findViewById(R.id.llTweetAttachInner);

        //添付データがある場合は設定する
        ArrayList<String> mediaUri = args.getStringArrayListExtra(EXTRA_MEDIA);
        if (args.getAction() != null && args.getType() != null &&
                args.getAction().equals(Intent.ACTION_SEND) && args.getType().startsWith("image/")) {
            attachPicture(args.getParcelableExtra(Intent.EXTRA_STREAM));
        }
        else if (mediaUri != null) {
            for (String s : mediaUri) {
                attachPicture(Uri.parse(s));
            }
        }

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
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    REQUEST_DIALOG_YUKARIN, null, "ゆっかりーん？", "\\ﾕｯｶﾘｰﾝ/", "(メ'ω')No"
            );
            dialogFragment.show(getSupportFragmentManager(), "yukarindlg");
            return true;
        });

        //各種サービスボタンの設定
        ImageButton ibCamera = (ImageButton) findViewById(R.id.ibTweetTakePic);
        ibCamera.setOnClickListener(v -> {
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
        });
        ImageButton ibAttach = (ImageButton) findViewById(R.id.ibTweetAttachPic);
        ibAttach.setOnClickListener(v -> {
            //添付上限判定
            if (maxMediaPerUpload > 1 && attachPictures.size() >= maxMediaPerUpload) {
                Toast.makeText(TweetActivity.this, "これ以上画像を添付できません。", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(TweetActivity.this, MultiPickerActivity.class);
            intent.putExtra(MultiPickerActivity.EXTRA_PICK_LIMIT, maxMediaPerUpload - attachPictures.size());
            intent.putExtra(MultiPickerActivity.EXTRA_THEME, theme.equals("light")? R.style.YukariLightTheme : R.style.YukariDarkTheme);
            intent.putExtra(MultiPickerActivity.EXTRA_CLOSE_ENTER_ANIMATION, R.anim.activity_tweet_close_enter);
            intent.putExtra(MultiPickerActivity.EXTRA_CLOSE_EXIT_ANIMATION, R.anim.activity_tweet_close_exit);
            startActivityForResult(intent, REQUEST_GALLERY);
            overridePendingTransition(R.anim.activity_tweet_open_enter, R.anim.activity_tweet_open_exit);
        });
        ibAttach.setOnLongClickListener(view -> {
            if (attachPictures.size() == 1 || (attachPictures.size() < maxMediaPerUpload)) {
                Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
                if (c.moveToLast()) {
                    long id = c.getLong(c.getColumnIndex(MediaStore.Images.Media._ID));
                    attachPicture(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                }
                c.close();
                Toast.makeText(TweetActivity.this, "最後に撮影した画像を添付します", Toast.LENGTH_LONG).show();
            }
            return true;
        });
        ImageButton ibHash = (ImageButton) findViewById(R.id.ibTweetSetHash);
        ibHash.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(TweetActivity.this)
                    .setTitle("ハッシュタグ入力")
                    .setOnCancelListener(dialog1 -> {
                        dialog1.dismiss();
                        currentDialog = null;
                    })
                    .setItems(new String[]{"入力履歴から", "タイムラインから"}, (dialog1, which) -> {
                        dialog1.dismiss();
                        currentDialog = null;

                        AlertDialog.Builder builder = new AlertDialog.Builder(TweetActivity.this);
                        final String[] hashtags;
                        switch (which) {
                            case 0:
                                builder.setTitle("ハッシュタグ入力履歴");
                                hashtags = usedHashes.getAll().toArray(new String[usedHashes.getAll().size()]);
                                break;
                            case 1:
                                builder.setTitle("TLで見かけたハッシュタグ");
                                hashtags = getTwitterService().getHashCache();
                                break;
                            default:
                                return;
                        }
                        builder.setItems(hashtags, (dialog2, which1) -> {
                            dialog2.dismiss();
                            currentDialog = null;

                            etInput.getText().append(" " + hashtags[which1]);
                        });
                        builder.setNegativeButton("キャンセル", (dialog2, which1) -> {
                            dialog2.dismiss();
                            currentDialog = null;
                        });
                        builder.setOnCancelListener(dialog2 -> {
                            dialog2.dismiss();
                            currentDialog = null;
                        });
                        AlertDialog ad = builder.create();
                        ad.show();
                        currentDialog = ad;
                    })
                    .create();
            dialog.show();
            currentDialog = dialog;
        });
        ibHash.setOnLongClickListener(v -> {
            appendTextInto(" #");
            return true;
        });
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
        ImageButton ibDraft = (ImageButton) findViewById(R.id.ibTweetDraft);
        ibDraft.setOnClickListener(v -> {
            AlertDialog ad = new AlertDialog.Builder(TweetActivity.this)
                    .setTitle("下書きメニュー")
                    .setOnCancelListener(dialog -> {
                        dialog.dismiss();
                        currentDialog = null;
                    })
                    .setItems(new String[] {"下書きを保存", "下書きを開く", "入力欄をクリア"}, (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;

                        switch (which) {
                            case 0: {
                                if (tweetCount >= tweetCountLimit && attachPictures.isEmpty()) {
                                    Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
                                }
                                else {
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
                    })
                    .create();
            ad.show();
            currentDialog = ad;
        });

        //各種エクストラボタンの設定
        final PackageManager pm = getPackageManager();
        ImageButton ibNowPlay = (ImageButton) findViewById(R.id.ibTweetNowPlaying);
        {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo("biz.Fairysoft.KoreKiiteru", 0);
                ibNowPlay.setVisibility(View.VISIBLE);
                ibNowPlay.setOnClickListener(v -> {
                    Intent intent = new Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT");
                    intent.addCategory("com.adamrocker.android.simeji.REPLACE");
                    intent.setPackage(ai.packageName);
                    startActivityForResult(intent, REQUEST_NOWPLAYING);
                });
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        ImageButton ibMorse = (ImageButton) findViewById(R.id.ibTweetMorseInput);
        {
            ibMorse.setOnClickListener(v -> {
                Intent intent = new Intent(TweetActivity.this, MorseInputActivity.class);
                startActivityForResult(intent, REQUEST_NOWPLAYING);
            });
        }
        ImageButton ibGrasses = (ImageButton) findViewById(R.id.ibTweetGrasses);
        ibGrasses.setOnClickListener(v -> {
            int start = etInput.getSelectionStart();
            int end = etInput.getSelectionEnd();
            if (start < 0 || end < 0 || start == end) {
                appendTextInto("ｗ");
            }
            else {
                String text = etInput.getText().toString().substring(Math.min(start, end), Math.max(start, end));
                char[] chr = text.toCharArray();
                int grass = text.length() - 1;
                StringBuilder sb = new StringBuilder();
                for (char c : chr) {
                    sb.append(c);
                    if (grass > 0) {
                        sb.append("ｗ");
                        --grass;
                    }
                }
                text = sb.toString();
                etInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
            }
        });
        ImageButton ibSanten = (ImageButton) findViewById(R.id.ibTweetSanten);
        ibSanten.setOnClickListener(v -> appendTextInto("…"));
        ibSNPicker = (ImageButton) findViewById(R.id.ibTweetSNPicker);
        ibSNPicker.setOnClickListener(v -> {
            Intent intent = new Intent(TweetActivity.this, SNPickerActivity.class);
            startActivityForResult(intent, REQUEST_SNPICKER);
        });


        //DM判定
        isDirectMessage = args.getIntExtra(EXTRA_MODE, MODE_TWEET) == MODE_DM;
        if (isDirectMessage) {
            directMessageDestId = args.getLongExtra(EXTRA_IN_REPLY_TO, -1);
            directMessageDestSN = args.getStringExtra(EXTRA_DM_TARGET_SN);
            //表題変更
            ((TextView)findViewById(R.id.tvTweetTitle)).setText("DirectMessage to @" + directMessageDestSN);
            //ボタン無効化と表示変更
            ibAttach.setEnabled(false);
            ibCamera.setEnabled(false);
            btnPost.setText("Send");
            //文字数上限変更
            tweetCount = tweetCountLimit = TWEET_COUNT_LIMIT_DM;
            updateTweetCount();
        }


        //プラグインロード
        Intent query = new Intent("jp.r246.twicca.ACTION_EDIT_TWEET");
        query.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> plugins = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
        Collections.sort(plugins, new ResolveInfo.DisplayNameComparator(pm));
        LinearLayout llTweetExtra = (LinearLayout) findViewById(R.id.llTweetExtra);
        final int iconSize = (int) (getResources().getDisplayMetrics().density * PLUGIN_ICON_DIP);
        for (ResolveInfo ri : plugins) {
            ImageButton imageButton = new ImageButton(this);
            Bitmap sourceIcon = ((BitmapDrawable)ri.activityInfo.loadIcon(pm)).getBitmap();
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

                Matcher prefixMatcher = PATTERN_PREFIX.matcher(text);
                String prefix = "";
                if (prefixMatcher.find()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < prefixMatcher.groupCount(); i++) {
                        sb.append(prefixMatcher.group(i+1));
                    }
                    prefix = sb.toString();
                }
                intent.putExtra("prefix", prefix);

                Matcher suffixMatcher = PATTERN_SUFFIX.matcher(text);
                String suffix = "";
                if (suffixMatcher.find() && suffixMatcher.groupCount() > 0) {
                    suffix = suffixMatcher.group(1);
                }
                intent.putExtra("suffix", suffix);

                Pattern userInputPattern = Pattern.compile(prefix + "(.+)" + suffix);
                Matcher userInputMatcher = userInputPattern.matcher(text);
                if (userInputMatcher.find() && userInputMatcher.groupCount() > 0) {
                    intent.putExtra("user_input", userInputMatcher.group(1));
                }
                else {
                    intent.putExtra("user_input", "");
                }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setFinishOnTouchOutside(false);
        }

        // 初期化完了時点での下書き状況のスナップショット
        initialDraft = (TweetDraft) getTweetDraft().clone();
    }

    private void postTweet() {
        if (tweetCount < 0) {
            Toast.makeText(TweetActivity.this, "ツイート上限文字数を超えています", Toast.LENGTH_SHORT).show();
            return;
        } else if (tweetCount >= tweetCountLimit && attachPictures.isEmpty()) {
            Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
            return;
        }
        if (writers.size() < 1) {
            Toast.makeText(TweetActivity.this, "アカウントを指定してください", Toast.LENGTH_SHORT).show();
            return;
        }

        //エイリアス処理
        String inputText = etInput.getText().toString();
        if (etInput.getText().toString().startsWith("::")) {
            String input = etInput.getText().toString();
            String command = input.split(" ")[0];
            switch (command) {
                case "::cmd":
                    startActivity(new Intent(getApplicationContext(), CommandsPrefActivity.class));
                    return;
                case "::main":
                    startActivity(new Intent(getApplicationContext(), MaintenanceActivity.class));
                    return;
                case "::sb":
                    inputText = "エビビーム！ﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞｗｗｗｗｗｗ";
                    break;
                case "::jb":
                    inputText = "Javaビームﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞwwwwwwwwww";
                    break;
                case "::bb":
                    inputText = input.replace("::bb", "@la0c bbop");
                    break;
                case "::cn":
                    if (inputText.split(" ").length > 1) {
                        String name = inputText.replace("::cn ", "");
                        new ThrowableTwitterAsyncTask<String, Void>() {
                            @Override
                            protected ThrowableResult<Void> doInBackground(String... params) {
                                try {
                                    Twitter twitter = getTwitterService().getTwitter();
                                    for (AuthUserRecord user : writers) {
                                        twitter.setOAuthAccessToken(user.getAccessToken());
                                        twitter.updateProfile(params[0], null, null, null);
                                    }
                                    return new ThrowableResult<>((Void) null);
                                } catch (TwitterException e) {
                                    e.printStackTrace();
                                    return new ThrowableResult<>(e);
                                }
                            }

                            @Override
                            protected void onPreExecute() {
                                super.onPreExecute();
                                showToast("Updating your name...");
                            }

                            @Override
                            protected void onPostExecute(ThrowableResult<Void> result) {
                                super.onPostExecute(result);
                                if (!result.isException()) {
                                    showToast("Updated your name!");
                                }
                            }

                            @Override
                            protected void showToast(String message) {
                                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                            }
                        }.execute(name);
                        setResult(RESULT_OK);
                        finish();
                        return;
                    } else {
                        Toast.makeText(getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
                        return;
                    }
                case "::d250g2":
                    if (inputText.split(" ").length > 1) {
                        String comment = inputText.replace("::d250g2 ", "");
                        inputText = comment + " http://twitpic.com/d250g2";
                    }
                    else {
                        inputText = "http://twitpic.com/d250g2";
                    }
                    break;
                case "::grgr":
                    inputText = "三('ω')三( ε: )三(.ω.)三( :3 )三('ω')三( ε: )三(.ω.)三( :3 )三('ω')三( ε: )三(.ω.)三( :3 )ゴロゴロゴロ";
                    break;
                case "::burn":
                    Toast.makeText(getApplicationContext(), "Sorry, burn command was rejected.", Toast.LENGTH_SHORT).show();
                    return;
                case "::sy":
                    inputText = "( ˘ω˘)ｽﾔｧ…";
                    break;
                case "::balus":
                    sendBroadcast(new Intent("shibafu.yukari.BALUS"));
                    setResult(RESULT_OK);
                    finish();
                    return;
                case "::batt":
                    inputText = batteryTweet;
                    break;
                case "::ay":
                    inputText = "#あひる焼き";
                    break;
                case "::mh": {
                    // Quote from https://github.com/0V/MohyoButton/blob/master/MohyoButton/Models/MohyoTweet.cs
                    final String[] MOHYO = {
                            "もひょ",
                            "もひょっ",
                            "もひょぉ",
                            "もひょもひょ",
                            "もひょもひょっ",
                            "もひょもひょぉ",
                            "もひょもひょもひょもひょ",
                            "＞ω＜もひょ",
                            "(~´ω`)~もひょ",
                            "~(´ω`~)もひょ",
                            "(～＞ω＜)～もひょ",
                            "～(＞ω＜～)もひょ",
                            "～(＞ω＜)～もひょ",
                            "進捗もひょです",
                            "Mohyo",
                            "mohyo",
                            "むいっ",
                    };
                    // End of quote
                    inputText = MOHYO[new Random().nextInt(MOHYO.length)];
                    break;
                }
                case "::ma":
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://wiki.famitsu.com/kairi/"))
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    setResult(RESULT_OK);
                    finish();
                    return;
                case "::meu":
                    inputText = "めめめめめめめ めうめうーっ！(」*ﾟﾛﾟ)」めめめ めうめうーっ！(」*ﾟﾛﾟ)」*ﾟﾛﾟ)」 ぺーったんぺったんぺったんぺったん 大好き～っ☆⌒ヽ(*'､＾*)";
                    break;
                case "::dice":
                    if (inputText.split(" ").length > 1) {
                        String diceInput = inputText.replace("::dice ", "");
                        Pattern pattern = Pattern.compile("(\\d+).(\\d+)");
                        Matcher m = pattern.matcher(diceInput);
                        if (m.find() && m.groupCount() == 2) {
                            int randomSum = 0;
                            Random r = new Random();
                            int count = Integer.parseInt(m.group(1));
                            int length = Integer.parseInt(m.group(2));
                            if (count < 1 || length < 1) {
                                Toast.makeText(getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            for (int i = 0; i < count; i++) {
                                randomSum += r.nextInt(length) + 1;
                            }
                            inputText = String.format("%dd%d => [%d]", count, length, randomSum);
                        } else {
                            Toast.makeText(getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        final String[] dice = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
                        Random r = new Random();
                        inputText = dice[r.nextInt(6)];
                    }
                    break;
                case "::yk": {
                    inputText = String.format("ゆかりさんゆかりさん！！(%d回目)", sp.getLong("count_yk", 1));
                    sp.edit().putLong("count_yk", sp.getLong("count_yk", 1) + 1).commit();
                    break;
                }
                case "::te": {
                    List<Template> templates = getTwitterService().getDatabase().getRecords(Template.class);
                    templateStrings = new ArrayList<>();
                    for (Template template : templates) {
                        templateStrings.add(template.getValue());
                    }
                    SimpleListDialogFragment dialogFragment = SimpleListDialogFragment.newInstance(
                            REQUEST_DIALOG_TEMPLATE, "定型文入力", null, null, "キャンセル", templateStrings
                    );
                    dialogFragment.show(getSupportFragmentManager(), "template");
                    return;
                }
                case "::td": {
                    startActivity(new Intent(getApplicationContext(), TemplateEditActivity.class));
                    return;
                }
            }
        }

        //ドラフトを作成
        TweetDraft draft = getTweetDraft();
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
        }
        else {
            //サービスに投げる
            Intent intent = PostService.newIntent(TweetActivity.this, draft);
            startService(intent);

            if (sp.getBoolean("first_guide", true)) {
                sp.edit().putBoolean("first_guide", false).commit();
            }

            setResult(RESULT_OK);
        }
        finish();
    }

    private void updateWritersView() {
        StringBuilder sb = new StringBuilder();
        if (writers.size() < 1) {
            sb.append(">> SELECT ACCOUNT(S)");
        }
        else for (int i = 0; i < writers.size(); ++i) {
            if (i > 0) sb.append("\n");
            sb.append("@");
            sb.append(writers.get(i).ScreenName);
        }
        tvTweetBy.setText(sb.toString());
    }

    private void showQuotedStatus() {
        TextView tvTitle = (TextView) findViewById(R.id.tvTweetTitle);
        if (status != null) {
            Status s = status.isRetweet()? status.getRetweetedStatus() : status;
            tvTitle.setText(String.format("Reply >> @%s: %s", s.getUser().getScreenName(), s.getText()));
        } else {
            tvTitle.setText("Reply >> load failed. (deleted or temporary error)");
        }
    }

    private int updateTweetCount() {
        int count = new Validator().getTweetLength(etInput.getText().toString());
        if (attachPictures.size() > 0) {
            reservedCount = shortUrlLength + 1;
        } else {
            reservedCount = 0;
        }
        tweetCount = tweetCountLimit - count - reservedCount;
        tvCount.setText(String.valueOf(tweetCount));
        if (tweetCount < 0) {
            tvCount.setTextColor(tweetCountOverColor);
        } else {
            tvCount.setTextColor(tweetCountColor);
        }
        return count + reservedCount;
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
                case REQUEST_CAMERA:
                {
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
                case REQUEST_VOICE:
                {
                    List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results.size() > 0) {
                        appendTextInto(results.get(0));
                    }
                    else {
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
                case REQUEST_ACCOUTS:
                {
                    writers = (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                    updateWritersView();
                    break;
                }
            }
        }
        else if (resultCode == RESULT_CANCELED) {
            switch (requestCode) {
                case REQUEST_CAMERA:
                {
                    try {
                        ContentResolver resolver = getContentResolver();
                        Cursor c = resolver.query(cameraTemp, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                        if (c != null) {
                            c.moveToFirst();
                            resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Images.Media.DATA + "=?",
                                    new String[]{c.getString(0)});
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
    protected void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
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

    private void attachPicture(final Uri uri) {
        AttachPicture pic = new AttachPicture();
        pic.uri = uri;
        try {
            int[] size = new int[2];
            Bitmap bmp = BitmapUtil.resizeBitmap(this, uri, 256, 256, size);
            pic.width = size[0];
            pic.height = size[1];
            pic.imageView = createAttachThumb(bmp);
            pic.imageView.setOnLongClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, uri, getApplicationContext(), PreviewActivity.class));
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
        }
        else {
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
    public void onDraftSelected(TweetDraft selected) {
        Intent intent = selected.getTweetIntent(this);

        startActivity(intent);
        finish();
    }

    private TweetDraft getTweetDraft() {
        TweetDraft draft = (TweetDraft) getIntent().getSerializableExtra(EXTRA_DRAFT);
        if (isDirectMessage) {
            if (draft == null) {
                draft = new TweetDraft(
                        writers,
                        etInput.getText().toString(),
                        System.currentTimeMillis(),
                        directMessageDestId,
                        directMessageDestSN,
                        false,
                        AttachPicture.toUriList(attachPictures),
                        false,
                        0,
                        0,
                        false,
                        false);
            } else {
                draft.updateFields(
                        writers,
                        etInput.getText().toString(),
                        directMessageDestId,
                        directMessageDestSN,
                        false,
                        AttachPicture.toUriList(attachPictures),
                        false,
                        0,
                        0,
                        false
                );
            }
        } else if (draft == null) {
            draft = new TweetDraft(
                    writers,
                    etInput.getText().toString(),
                    System.currentTimeMillis(),
                    (status == null) ? -1 : status.getId(),
                    false,
                    AttachPicture.toUriList(attachPictures),
                    false,
                    0,
                    0,
                    false,
                    false);
        } else {
            draft.updateFields(
                    writers,
                    etInput.getText().toString(),
                    (status == null) ? -1 : status.getId(),
                    false,
                    AttachPicture.toUriList(attachPictures),
                    false,
                    0,
                    0,
                    false
            );
        }
        setIntent(getIntent().putExtra(EXTRA_DRAFT, draft));
        return draft;
    }

    @Override
    public void onServiceConnected() {
        if (useStoredWriters && writers.size() == 0) {
            writers = getTwitterService().getWriterUsers();
            updateWritersView();
            initialDraft.setWriters(writers);
        }

        TwitterAPIConfiguration apiConfiguration = getTwitterService().getApiConfiguration();
        if (apiConfiguration != null) {
            maxMediaPerUpload = 4;//apiConfiguration.getMaxMediaPerUpload();
            ((TextView)findViewById(R.id.tvTweetAttach)).setText("Attach (max:" + maxMediaPerUpload + ")");
            shortUrlLength = apiConfiguration.getShortURLLength();
        }
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public void onDialogChose(int requestCode, int which) {
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
            case REQUEST_DIALOG_TEMPLATE:
                if (which > -1) {
                    etInput.setText(templateStrings.get(which));
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

    private static class AttachPicture {
        public Uri uri = null;
        public int width = -1;
        public int height = -1;
        public ImageView imageView;

        public static List<Uri> toUriList(List<AttachPicture> from) {
            List<Uri> list = new ArrayList<>();
            for (AttachPicture ap : from) {
                list.add(ap.uri);
            }
            return list;
        }
    }
}
