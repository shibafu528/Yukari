package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * Created by Shibafu on 13/08/01.
 */
public class OAuthActivity extends Activity{

    private static final String CALLBACK_URL = "yukari://twitter";
    private Twitter twitter;
    private RequestToken requestToken;

    private TwitterService service;
    private boolean serviceBound;
    private boolean begunOAuth = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        twitter = TwitterUtil.getTwitterInstance(this);
        startOAuth();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (begunOAuth) {
            Toast.makeText(OAuthActivity.this, "認証が中断されました", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private void startOAuth() {
        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    requestToken = twitter.getOAuthRequestToken(CALLBACK_URL);
                    return requestToken.getAuthorizationURL();
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                if (s != null) {
                    begunOAuth = true;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
                    startActivity(intent);
                }
                else {
                    Toast.makeText(OAuthActivity.this, "認証の準備プロセスでエラーが発生しました", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        };
        task.execute();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        begunOAuth = false;

        //コールバック以外のintentが流れ込んで来たらエラー
        if (intent == null || intent.getData() == null || !intent.getData().toString().startsWith(CALLBACK_URL))
            return;

        String verifier = intent.getData().getQueryParameter("oauth_verifier");

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Progress...");
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        AsyncTask<String, Void, AccessToken> task = new AsyncTask<String, Void, AccessToken>() {
            @Override
            protected AccessToken doInBackground(String... params) {
                if (params[0] == null) return null;
                try {
                    AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, params[0]);
                    while (!serviceBound) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    AuthUserRecord userRecord = new AuthUserRecord(accessToken);
                    userRecord.isActive = true;
                    service.getDatabase().addAccount(userRecord);
                    User user = twitter.showUser(accessToken.getUserId());
                    service.getDatabase().updateUser(new DBUser(user));

                    service.reloadUsers();

                    return accessToken;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(AccessToken accessToken) {
                dialog.dismiss();
                if (accessToken != null) {
                    Toast.makeText(OAuthActivity.this, "認証成功", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                }
                else {
                    Toast.makeText(OAuthActivity.this, "認証に失敗しました", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        };
        task.execute(verifier);
        dialog.show();
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            OAuthActivity.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
