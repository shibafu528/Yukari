package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import java.util.List;

import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/08/09.
 */
public class StatusIntentActivity extends Activity{

    private Twitter twitter;
    private ProgressDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        twitter = TwitterUtil.getTwitterInstance(this);

        Uri argData = getIntent().getData();
        List<String> segments = argData.getPathSegments();

        final String from = segments.get(0);
        final long tweetId = Long.valueOf(segments.get(2));

        final AsyncTask<Void, Void, Status> task = new AsyncTask<Void, Void, Status>() {
            @Override
            protected twitter4j.Status doInBackground(Void... params) {
                try {
                    twitter4j.Status status = twitter.showStatus(tweetId);
                    if (isCancelled()) return null;
                    return status;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(twitter4j.Status status) {
                if (currentDialog != null) {
                    currentDialog.dismiss();
                    currentDialog = null;
                }

                if (status != null) {
                    Intent intent = new Intent(StatusIntentActivity.this, StatusActivity.class);
                    intent.putExtra(StatusActivity.EXTRA_STATUS, status);
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
        };

        currentDialog = new ProgressDialog(this);
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
}
