package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.CountDownLatch;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.YukariBase;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
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
public class OAuthActivity extends YukariBase {

    private final int TWITTER_REQUEST_CODE = 1;
    private static final ComponentName TWITTER_AUTH_ACTIVITY = new ComponentName("com.twitter.android", "com.twitter.android.AuthorizeAppActivity");

    public static final String EXTRA_REBOOT = "reboot";

    private static final String CALLBACK_URL = "yukari://twitter";
    private Twitter twitter;
    private RequestToken requestToken;

    private CountDownLatch serviceLatch;
    private boolean begunOAuth = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);


        boolean foundTwitter = false;
        PackageManager pm = getPackageManager();
        try {
            pm.getActivityInfo(TWITTER_AUTH_ACTIVITY, PackageManager.GET_ACTIVITIES);
            foundTwitter = true;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        twitter = TwitterUtil.getTwitterInstance(this);

        if (foundTwitter) {
            new AlertDialog.Builder(this)
                    .setTitle("認証方法を選択")
                    .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    })
                    .setItems(new String[]{"ブラウザを起動...", "[非推奨] Twitter公式アプリ"}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            switch (which) {
                                case 0:
                                    startOAuth();
                                    break;
                                case 1:
                                {
                                    begunOAuth = true;
                                    Intent intent = new Intent().setComponent(TWITTER_AUTH_ACTIVITY);
                                    intent.putExtra("ck", getString(R.string.twitter_consumer_key));
                                    intent.putExtra("cs", getString(R.string.twitter_consumer_secret));
                                    try {
                                        startActivityForResult(intent, TWITTER_REQUEST_CODE);
                                    } catch (SecurityException e) {
                                        e.printStackTrace();
                                        Toast.makeText(getApplicationContext(), "実行権限が不足しています。ブラウザでの認証に切り替えます。", Toast.LENGTH_LONG).show();
                                        startOAuth();
                                    }
                                    break;
                                }
                            }
                        }
                    })
                    .create().show();
        }
        else {
            startOAuth();
        }
    }

    @Override
    protected void onStart() {
        serviceLatch = new CountDownLatch(1);
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (begunOAuth) {
            Bundle extras = getIntent().getExtras();
            try {
                if (extras == null) throw new IllegalArgumentException();
                AccessToken accessToken = (AccessToken) extras.getSerializable("atk");
                if (accessToken != null) {
                    saveAccessToken(accessToken);
                }
                else throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                Toast.makeText(OAuthActivity.this, "認証が中断されました", Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("rq", requestToken);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        requestToken = (RequestToken) savedInstanceState.getSerializable("rq");
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = super.onCreateDialog(id);
        if (id == 1) {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Progress...");
            progressDialog.setIndeterminate(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setCancelable(false);
            dialog = progressDialog;
        }
        return dialog;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TWITTER_REQUEST_CODE && data != null) {
            Bundle extras = data.getExtras();
            AccessToken accessToken = new AccessToken(
                    extras.getString("tk"),
                    extras.getString("ts"),
                    extras.getLong("user_id"));
            Intent intent = new Intent(getIntent());
            intent.putExtra("atk", accessToken);
            setIntent(intent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        begunOAuth = false;

        //コールバック以外のintentが流れ込んで来たらエラー
        if (intent == null || intent.getData() == null || !intent.getData().toString().startsWith(CALLBACK_URL))
            return;

        String verifier = intent.getData().getQueryParameter("oauth_verifier");

        AsyncTask<String, Void, AccessToken> task = new AsyncTask<String, Void, AccessToken>() {
            @Override
            protected AccessToken doInBackground(String... params) {
                if (params[0] == null) return null;
                try {
                    return twitter.getOAuthAccessToken(requestToken, params[0]);
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(AccessToken accessToken) {
                dismissDialog(1);
                if (accessToken != null) {
                    saveAccessToken(accessToken);
                }
                else {
                    Toast.makeText(OAuthActivity.this, "認証に失敗しました", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        };
        task.execute(verifier);
        showDialog(1);
    }

    private void saveAccessToken(AccessToken accessToken) {
        AsyncTask<AccessToken, Void, Boolean> task = new AsyncTask<AccessToken, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(AccessToken... params) {
                try {
                    AccessToken accessToken = params[0];
                    try {
                        serviceLatch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    twitter = getTwitterService().getTwitter();
                    twitter.setOAuthAccessToken(accessToken);

                    AuthUserRecord userRecord = new AuthUserRecord(accessToken);
                    userRecord.isActive = true;

                    CentralDatabase database = getTwitterService().getDatabase();
                    database.addAccount(userRecord);
                    User user = twitter.showUser(accessToken.getUserId());
                    database.updateRecord(new DBUser(user));

                    getTwitterService().reloadUsers();
                    return true;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            protected void onPreExecute() {
                showDialog(1);
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                dismissDialog(1);
                if (aBoolean) {
                    Toast.makeText(OAuthActivity.this, "認証成功", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    if (getIntent().getBooleanExtra(EXTRA_REBOOT, false)) {
                        startActivity(new Intent(OAuthActivity.this, MainActivity.class));
                    }
                    finish();
                }
                else {
                    Toast.makeText(OAuthActivity.this, "認証に失敗しました", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        };
        task.execute(accessToken);
    }

    @Override
    public void onServiceConnected() {
        serviceLatch.countDown();
        Log.d("OAuthActivity", "Bound Service.");
    }

    @Override
    public void onServiceDisconnected() {
        Log.d("OAuthActivity", "Unbound Service.");
    }
}
