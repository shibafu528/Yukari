package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import java.util.List;

import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/08/09.
 */
public class StatusIntentActivity extends Activity{

    private Twitter twitter;
    private AuthUserRecord primaryUser;
    private ProgressDialog currentDialog;

    private TwitterService service;
    private boolean serviceBound = false;

    private long requestTweetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        twitter = TwitterUtil.getTwitterInstance(this);

        Uri argData = getIntent().getData();
        List<String> segments = argData.getPathSegments();

        final String from = segments.get(0);
        requestTweetId = Long.valueOf(segments.get(2));
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
            StatusIntentActivity.this.service = binder.getService();
            serviceBound = true;

            primaryUser = StatusIntentActivity.this.service.getPrimaryUser();

            final TweetLoaderTask task = new TweetLoaderTask();

            currentDialog = new ProgressDialog(StatusIntentActivity.this);
            currentDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            currentDialog.setIndeterminate(true);
            currentDialog.setMessage("読み込み中...");
            currentDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    task.cancel(true);
                }
            });
            currentDialog.show();

            task.execute();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    class TweetLoaderTask extends AsyncTask<Void, Void, PreformedStatus> {

        @Override
        protected PreformedStatus doInBackground(Void... params) {
            try {
                twitter4j.Status status = twitter.showStatus(requestTweetId);
                PreformedStatus ps = new PreformedStatus(status, primaryUser);
                if (isCancelled()) return null;
                return ps;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(PreformedStatus status) {
            if (currentDialog != null) {
                currentDialog.dismiss();
                currentDialog = null;
            }

            if (status != null) {
                Intent intent = new Intent(StatusIntentActivity.this, StatusActivity.class);
                intent.putExtra(StatusActivity.EXTRA_STATUS, status);
                intent.putExtra(StatusActivity.EXTRA_USER, primaryUser);
                startActivity(intent);
                finish();
            }
            else if (isCancelled()) {
                //
            }
            else {
                Toast.makeText(StatusIntentActivity.this, "ツイートの受信に失敗しました", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled() {
            if (currentDialog != null) {
                currentDialog.dismiss();
                currentDialog = null;
            }
        }
    }
}
