package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * Created by Shibafu on 13/08/01.
 */
public class OAuthActivity extends Activity{

    private static final String CALLBACK_URL = "yukari://twitter";
    private Twitter twitter;
    private RequestToken requestToken;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        twitter = TwitterUtil.getTwitterInstance(this);
        startOAuth();
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
                try {
                    return twitter.getOAuthAccessToken(requestToken, params[0]);
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
                    TwitterUtil.storeUserRecord(OAuthActivity.this, new AuthUserRecord(accessToken));
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
}
