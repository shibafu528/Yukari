package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.util.BitmapResizer;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.fragment.DraftDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;
import twitter4j.util.CharacterUtil;

public class TweetActivity extends FragmentActivity implements DraftDialogFragment.DraftDialogEventListener{

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_REPLY = "reply";
    public static final String EXTRA_DM = "dm";
    public static final String EXTRA_DM_TARGET = "dm_to";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MEDIA = "media";

    private static final int REQUEST_GALLERY = 1;
    private static final int REQUEST_CAMERA = 2;
    private static final int REQUEST_VOICE = 3;
    private static final int REQUEST_NOWPLAYING = 32;
    private static final int REQUEST_TOTSUSI = 33;

    private EditText etInput;
    private TextView tvCount;
    private int tweetCount = 140;
    private int reservedCount = 0;

    private boolean isDirectMessage = false;
    private String directMessageDestSN;

    private LinearLayout llTweetAttachParent;
    private LinearLayout llTweetAttach;
    private ImageView ivAttach;

    private AuthUserRecord user;
    private Status status;
    private AttachPicture attachPicture;

    private TwitterService service;
    private boolean serviceBound = false;

    private Uri cameraTemp;

    private ProgressDialog progress;
    private AlertDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_tweet);

        //Extraを取得
        Intent args = getIntent();
        Uri dataArg = args.getData();

        //ユーザー指定がある場合は表示する (EXTRA_USER)
        TextView tvTweetBy = (TextView) findViewById(R.id.tvTweetBy);
        user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);
        if (user != null) {
            tvTweetBy.setText("@" + user.ScreenName);
        }

        //statusを取得する (EXTRA_STATUS)
        status = (Status) args.getSerializableExtra(EXTRA_STATUS);

        //WebIntent判定
        boolean isWebIntent = false;
        if (dataArg != null && dataArg.getHost().equals("twitter.com")) {
            isWebIntent = true;
        }

        //IMEの表示設定
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        //テキストエリアの設定
        tvCount = (TextView) findViewById(R.id.tvTweetCount);
        etInput = (EditText) findViewById(R.id.etTweetInput);
        etInput.setTypeface(FontAsset.getInstance(this).getFont());
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                int count = CharacterUtil.count(s.toString());
                tweetCount = 140 - count - reservedCount;
                tvCount.setText(String.valueOf(tweetCount));
                if (count < 0) {
                    tvCount.setTextColor(Color.RED);
                } else {
                    tvCount.setTextColor(Color.BLACK);
                }
            }
        });
        etInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        //デフォルト文章が設定されている場合は入力しておく (EXTRA_TEXT)
        String defaultText = "";
        if (isWebIntent) {
            String paramInReplyTo = dataArg.getQueryParameter("in_reply_to");
            String paramText = dataArg.getQueryParameter("text");
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
                    sb.append(" ");
                    sb.append(t);
                }
            }
            defaultText = sb.toString();
        }
        else if (args.getAction() != null && args.getType() != null &&
                args.getAction().equals(Intent.ACTION_SEND) && args.getType().startsWith("text/")) {
            defaultText = args.getDataString();
            if (defaultText == null) {
                defaultText = args.getStringExtra(Intent.EXTRA_TEXT);
            }
        }
        else {
            defaultText = args.getStringExtra(EXTRA_TEXT);
        }
        etInput.setText((defaultText != null)?defaultText : "");
        if (args.getBooleanExtra(EXTRA_REPLY, false))
            etInput.setSelection(etInput.getText().length());

        //添付エリアの設定
        llTweetAttachParent = (LinearLayout) findViewById(R.id.llTweetAttachParent);
        llTweetAttach = (LinearLayout) findViewById(R.id.llTweetAttach);
        ivAttach = (ImageView) findViewById(R.id.ivTweetImageAttach);
        ivAttach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (attachPicture != null) {
                    AlertDialog ad = new AlertDialog.Builder(TweetActivity.this)
                            .setTitle("添付の取り消し")
                            .setMessage("画像の添付を取り消してもよろしいですか？")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton("はい", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                    attachPicture = null;

                                    llTweetAttachParent.setVisibility(View.GONE);
                                }
                            })
                            .setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .create();
                    ad.show();
                    currentDialog = ad;
                }
            }
        });

        //添付データがある場合は設定する
        String[] strMedia = args.getStringArrayExtra(EXTRA_MEDIA);
        if (args.getAction() != null && args.getType() != null &&
                args.getAction().equals(Intent.ACTION_SEND) && args.getType().startsWith("image/")) {
            addAttachPicture((Uri) args.getParcelableExtra(Intent.EXTRA_STREAM));
        }
        else if (strMedia != null) {
            for (String s : strMedia) {
                Uri uri = Uri.parse(s);
                addAttachPicture(uri);
            }
        }

        //投稿ボタンの設定
        Button btnPost = (Button) findViewById(R.id.btnTweet);
        btnPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tweetCount < 0) {
                    Toast.makeText(TweetActivity.this, "ツイート上限文字数を超えています", Toast.LENGTH_SHORT).show();
                    return;
                }
                else if (tweetCount >= 140) {
                    Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!serviceBound) {
                    Toast.makeText(TweetActivity.this, "サービスが停止しています", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isDirectMessage && user == null) {
                    Toast.makeText(TweetActivity.this, "相互フォローになっている、送信元アカウント指定が必要です", Toast.LENGTH_SHORT).show();
                    return;
                }

                AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            if (isDirectMessage) {
                                service.sendDirectMessage(directMessageDestSN, user, etInput.getText().toString());
                            }
                            else {
                                StatusUpdate update = new StatusUpdate(etInput.getText().toString());
                                //statusが引数に付加されている場合はin-reply-toとして設定する
                                if (status != null) {
                                    update.setInReplyToStatusId(status.getId());
                                }
                                //attachPictureがある場合は添付
                                if (attachPicture != null) {
                                    try {
                                        if (Math.max(attachPicture.width, attachPicture.height) > 2048) {
                                            Log.d("TweetActivity", "添付画像の長辺が2048pxを超えています。圧縮対象とします。");
                                            Bitmap resized = BitmapResizer.resizeBitmap(TweetActivity.this, attachPicture.uri, 1920, 1920, null);
                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            resized.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                                            Log.d("TweetActivity", "縮小しました w=" + resized.getWidth() + " h=" + resized.getHeight());
                                            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                                            service.postTweet(user, update, new InputStream[]{bais});
                                        }
                                        else {
                                            service.postTweet(user, update, new InputStream[]{getContentResolver().openInputStream(attachPicture.uri)});
                                        }
                                        return true;
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return false;
                                }
                                service.postTweet(user, update);
                            }
                            return true;
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return false;
                    }

                    @Override
                    protected void onPostExecute(Boolean aBoolean) {
                        if (progress != null) {
                            progress.dismiss();
                            progress = null;
                        }
                        if (aBoolean) {
                            Toast.makeText(TweetActivity.this, "投稿しました", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        else {
                            Toast.makeText(TweetActivity.this, "通信中にエラーが発生しました", Toast.LENGTH_SHORT).show();
                        }
                    }
                };
                task.execute();
                ProgressDialog pd = new ProgressDialog(TweetActivity.this);
                pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                pd.setCancelable(false);
                pd.setMessage("送信中...");
                pd.show();
                progress = pd;
            }
        });

        //各種サービスボタンの設定
        ImageButton ibCamera = (ImageButton) findViewById(R.id.ibTweetTakePic);
        ibCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });
        ImageButton ibAttach = (ImageButton) findViewById(R.id.ibTweetAttachPic);
        ibAttach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_GALLERY);
            }
        });
        ImageButton ibHash = (ImageButton) findViewById(R.id.ibTweetSetHash);
        ibHash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(TweetActivity.this);
                final String[] hashCache = service.getHashCache();
                builder.setTitle("TLで見かけたハッシュタグ");
                builder.setItems(hashCache, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        currentDialog = null;

                        appendTextInto(hashCache[which]);
                    }
                });
                builder.setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        currentDialog = null;
                    }
                });
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        currentDialog = null;
                    }
                });
                AlertDialog ad = builder.create();
                ad.show();
                currentDialog = ad;
            }
        });
        ibHash.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                appendTextInto("#");
                return true;
            }
        });
        ImageButton ibVoice = (ImageButton) findViewById(R.id.ibTweetVoiceInput);
        ibVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "ツイートする内容をお話しください");
                try {
                    startActivityForResult(intent, REQUEST_VOICE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(TweetActivity.this, "音声入力が組み込まれていません", Toast.LENGTH_SHORT).show();
                }
            }
        });
        ImageButton ibDraft = (ImageButton) findViewById(R.id.ibTweetDraft);
        ibDraft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog ad = new AlertDialog.Builder(TweetActivity.this)
                        .setTitle("下書きメニュー")
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.dismiss();
                                currentDialog = null;
                            }
                        })
                        .setItems(new String[] {"下書きを保存", "下書きを開く"}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                switch (which) {
                                    case 0:
                                    {
                                        if (tweetCount >= 140 && attachPicture == null) {
                                            Toast.makeText(TweetActivity.this, "なにも入力されていません", Toast.LENGTH_SHORT).show();
                                        }
                                        else {
                                            TweetDraft draft;
                                            if (attachPicture == null)
                                                draft = new TweetDraft(user, etInput.getText().toString(), status,
                                                        (String[]) null);
                                            else
                                                draft = new TweetDraft(user, etInput.getText().toString(), status,
                                                        new String[]{attachPicture.uri.toString()});
                                            try {
                                                List<TweetDraft> tweetDrafts = TweetDraft.loadDrafts(TweetActivity.this);
                                                if (tweetDrafts == null) {
                                                    tweetDrafts = new ArrayList<TweetDraft>();
                                                }
                                                tweetDrafts.add(draft);
                                                TweetDraft.saveDrafts(TweetActivity.this, tweetDrafts);
                                                Toast.makeText(TweetActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                Toast.makeText(TweetActivity.this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                        break;
                                    }
                                    case 1:
                                    {
                                        DraftDialogFragment draftDialogFragment = new DraftDialogFragment();
                                        draftDialogFragment.show(getSupportFragmentManager(), "draftDialog");
                                        break;
                                    }
                                }
                            }
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            }
        });

        //各種エクストラボタンの設定
        PackageManager pm = getPackageManager();
        ImageButton ibNowPlay = (ImageButton) findViewById(R.id.ibTweetNowPlaying);
        {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo("biz.Fairysoft.KoreKiiteru", 0);
                ibNowPlay.setVisibility(View.VISIBLE);
                ibNowPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT");
                        intent.addCategory("com.adamrocker.android.simeji.REPLACE");
                        intent.setPackage(ai.packageName);
                        startActivityForResult(intent, REQUEST_NOWPLAYING);
                    }
                });
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        ImageButton ibMorse = (ImageButton) findViewById(R.id.ibTweetMorseInput);
        {
            ibMorse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(TweetActivity.this, MorseInputActivity.class);
                    startActivityForResult(intent, REQUEST_NOWPLAYING);
                }
            });
        }
        ImageButton ibTotsusi = (ImageButton) findViewById(R.id.ibTweetTotsusi);
        {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo("info.izumin.android.suddenlydeathmush", 0);
                ibTotsusi.setVisibility(View.VISIBLE);
                ibTotsusi.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT");
                        intent.addCategory("com.adamrocker.android.simeji.REPLACE");
                        intent.putExtra("replace_key", "");
                        intent.setPackage(ai.packageName);
                        startActivityForResult(intent, REQUEST_TOTSUSI);
                    }
                });
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        ImageButton ibGrasses = (ImageButton) findViewById(R.id.ibTweetGrasses);
        ibGrasses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            }
        });
        ImageButton ibSanten = (ImageButton) findViewById(R.id.ibTweetSanten);
        ibSanten.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendTextInto("…");
            }
        });


        //DM判定
        isDirectMessage = args.getBooleanExtra(EXTRA_DM, false);
        if (isDirectMessage) {
            directMessageDestSN = args.getStringExtra(EXTRA_DM_TARGET);
            //表題変更
            ((TextView)findViewById(R.id.tvTweetTitle)).setText("DirectMessage to @" + directMessageDestSN);
            //ボタン無効化と表示変更
            ibAttach.setEnabled(false);
            ibCamera.setEnabled(false);
            ibDraft.setEnabled(false);
            btnPost.setText("Send");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_GALLERY:
                    addAttachPicture(data.getData());
                    break;
                case REQUEST_CAMERA:
                {
                    if (cameraTemp == null && data.getData() == null) {
                        Toast.makeText(TweetActivity.this, "カメラとの連携に失敗しました。\n使用したカメラアプリとの相性かもしれません。", Toast.LENGTH_LONG).show();
                        return;
                    }
                    else if (data.getData() != null) {
                        //getDataでUriが返ってくる端末用
                        //フィールドは手に入ったUriで上書き
                        cameraTemp = data.getData();
                    }
                    //添付に追加する
                    addAttachPicture(cameraTemp);
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
            }
        }
        else if (resultCode == RESULT_CANCELED) {
            switch (requestCode) {
                case REQUEST_CAMERA:
                {
                    Cursor c = getContentResolver().query(cameraTemp, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                    c.moveToFirst();
                    getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Images.Media.DATA + "=?",
                            new String[]{c.getString(0)});
                    break;
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private void addAttachPicture(Uri uri) {
        AttachPicture pic = new AttachPicture();
        pic.uri = uri;
        try {
            int[] size = new int[2];
            Bitmap bmp = BitmapResizer.resizeBitmap(this, uri, 256, 256, size);
            pic.width = size[0];
            pic.height = size[1];
            ivAttach.setImageBitmap(bmp);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        attachPicture = pic;
        llTweetAttachParent.setVisibility(View.VISIBLE);
    }

    private void appendTextInto(String text) {
        int start = etInput.getSelectionStart();
        int end = etInput.getSelectionEnd();
        etInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TweetActivity.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public void onDraftSelected(TweetDraft selected) {
        Intent intent = new Intent(this, TweetActivity.class);
        intent.putExtra(EXTRA_TEXT, selected.text);
        intent.putExtra(EXTRA_USER, selected.user);
        intent.putExtra(EXTRA_REPLY, selected.from != null);
        intent.putExtra(EXTRA_STATUS, selected.from);
        intent.putExtra(EXTRA_MEDIA, selected.attachMedia);
        startActivity(intent);
        finish();
    }

    private class AttachPicture {
        public Uri uri = null;
        public int width = -1;
        public int height = -1;
    }
}
