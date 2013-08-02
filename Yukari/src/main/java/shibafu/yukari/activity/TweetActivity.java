package shibafu.yukari.activity;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.common.VLPGothic;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;
import twitter4j.util.CharacterUtil;

public class TweetActivity extends Activity {

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_REPLY = "reply";
    public static final String EXTRA_TEXT = "text";

    private EditText etInput;
    private TextView tvCount;
    private int tweetCount = 140;

    private AuthUserRecord user;
    private Status status;

    private TwitterService service;
    private boolean serviceBound = false;

    private ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tweet);

        TextView tvTweetBy = (TextView) findViewById(R.id.tvTweetBy);

        //Extraを取得
        Intent args = getIntent();

        //ユーザー指定がある場合は表示する (EXTRA_USER)
        user = (AuthUserRecord) args.getSerializableExtra(EXTRA_USER);
        if (user != null) {
            tvTweetBy.setText("@" + user.ScreenName);
        }

        //statusを取得する (EXTRA_STATUS)
        status = (Status) args.getSerializableExtra(EXTRA_STATUS);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        tvCount = (TextView) findViewById(R.id.tvTweetCount);
        etInput = (EditText) findViewById(R.id.etTweetInput);
        etInput.setTypeface(VLPGothic.getInstance(this).getFont());
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
                tweetCount = 140 - count;
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
        String defaultText = args.getStringExtra(EXTRA_TEXT);
        etInput.setText((defaultText != null)?defaultText : "");
        if (args.getBooleanExtra(EXTRA_REPLY, false))
            etInput.setSelection(etInput.getText().length());

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


                AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            StatusUpdate update = new StatusUpdate(etInput.getText().toString());
                            //statusが引数に付加されている場合はin-reply-toとして設定する
                            if (status != null) {
                                update.setInReplyToStatusId(status.getId());
                            }
                            service.postTweet(user, update);
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
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
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
}
