package shibafu.yukari.smallapp;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.sony.smallapp.SmallAppWindow;
import com.sony.smallapp.SmallApplication;

import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceConnection;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.util.CharacterUtil;

/**
 * Created by shibafu on 14/11/20.
 */
public class SmallTweet extends SmallApplication implements TwitterServiceConnection.ServiceConnectionCallback, TwitterServiceDelegate {
    private TwitterServiceConnection servicesConnection = new TwitterServiceConnection(this);
    private List<AuthUserRecord> accounts;
    private AuthUserRecord selectedAccount;
    private ImageButton ibSelectAccount;
    private EditText etTweet;
    private InputMethodManager imm;

    @Override
    protected void onCreate() {
        super.onCreate();
        setContentView(R.layout.small_tweet);
        setTitle("Tweet with Yukari");

        SmallAppWindow.Attributes attr = getWindow().getAttributes();
        attr.width = getResources().getDimensionPixelSize(R.dimen.small_app_width);
        attr.height = getResources().getDimensionPixelSize(R.dimen.small_app_height);
        attr.flags ^= SmallAppWindow.Attributes.FLAG_RESIZABLE;
        getWindow().setAttributes(attr);

        imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        ibSelectAccount = (ImageButton) findViewById(R.id.ibAccount);
        ibSelectAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = accounts.indexOf(selectedAccount);
                if (i == accounts.size() - 1) {
                    selectedAccount = accounts.get(0);
                } else {
                    selectedAccount = accounts.get(i+1);
                }
                ImageLoaderTask.loadProfileIcon(SmallTweet.this, ibSelectAccount, selectedAccount.ProfileImageUrl);
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

        ImageButton ibTweet = (ImageButton) findViewById(R.id.ibTweet);
        ibTweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postTweet();
            }
        });
    }

    private void postTweet() {
        if (selectedAccount == null) {
            Toast.makeText(this, "アカウントが選択されていません", Toast.LENGTH_LONG).show();
        }
        else if (etTweet.getText().length() < 1) {
            Toast.makeText(this, "テキストが入力されていません", Toast.LENGTH_LONG).show();
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
            etTweet.requestFocus();
            imm.showSoftInput(etTweet, InputMethodManager.SHOW_FORCED);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        servicesConnection.connect(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        servicesConnection.disconnect(this);
    }

    @Override
    public boolean isTwitterServiceBound() {
        return servicesConnection.isServiceBound();
    }

    @Override
    public TwitterService getTwitterService() {
        return servicesConnection.getTwitterService();
    }

    @Override
    public void onServiceConnected() {
        accounts = getTwitterService().getUsers();
        selectedAccount = getTwitterService().getPrimaryUser();
        if (selectedAccount == null) {
            Toast.makeText(this, "プライマリアカウントが取得できません\nクイック投稿は無効化されます", Toast.LENGTH_LONG).show();
            finish();
        } else {
            ImageLoaderTask.loadProfileIcon(this, ibSelectAccount, selectedAccount.ProfileImageUrl);
        }
    }

    @Override
    public void onServiceDisconnected() {

    }
}
