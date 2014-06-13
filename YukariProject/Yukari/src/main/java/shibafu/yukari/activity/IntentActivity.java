package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Pair;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/08/09.
 */
public class IntentActivity extends Activity{

    private static final List<Pair<Pattern, AfterWork>> MATCHES;
    static {
        List<Pair<Pattern, AfterWork>> matchTemp = new ArrayList<>();
        matchTemp.add(new Pair<Pattern, AfterWork>(
                Pattern.compile("^https?://twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)$"),
                new AfterWork() {
                    @Override
                    public void work(IntentActivity activity) {
                        String id = activity.matchedWork.first.group(1);
                        TweetLoaderTask task = activity.new TweetLoaderTask();
                        task.execute(Long.valueOf(id));
                    }
                }));
        matchTemp.add(new Pair<Pattern, AfterWork>(
                Pattern.compile("^https?://twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/?$"),
                new AfterWork() {
                    @Override
                    public void work(IntentActivity activity) {
                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                activity.getIntent().getData(),
                                activity.getApplicationContext(),
                                ProfileActivity.class
                        );
                        activity.startActivity(intent);
                        activity.finish();
                    }
                }));
        MATCHES = Collections.unmodifiableList(matchTemp);
    }

    private Twitter twitter;
    private AuthUserRecord primaryUser;

    private TwitterService service;
    private boolean serviceBound = false;

    private Pair<Matcher, AfterWork> matchedWork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getDataString();
        for (Pair<Pattern, AfterWork> pair : MATCHES) {
            Matcher matcher = pair.first.matcher(url);
            if (matcher.find()) {
                matchedWork = new Pair<>(matcher, pair.second);
                break;
            }
        }
        if (matchedWork == null) {
            Toast.makeText(getApplicationContext(), "非対応URLです.", Toast.LENGTH_SHORT).show();
            finish();
        }
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
            IntentActivity.this.service = binder.getService();
            serviceBound = true;

            twitter = IntentActivity.this.service.getTwitter();
            primaryUser = IntentActivity.this.service.getPrimaryUser();

            matchedWork.second.work(IntentActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class TweetLoaderTask extends AsyncTask<Long, Void, PreformedStatus> {

        private ProgressDialog currentDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            currentDialog = new ProgressDialog(IntentActivity.this);
            currentDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            currentDialog.setIndeterminate(true);
            currentDialog.setMessage("読み込み中...");
            currentDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            currentDialog.show();
        }

        @Override
        protected PreformedStatus doInBackground(Long... params) {
            try {
                twitter4j.Status status = twitter.showStatus(params[0]);
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
                Intent intent = new Intent(IntentActivity.this, StatusActivity.class);
                intent.putExtra(StatusActivity.EXTRA_STATUS, status);
                intent.putExtra(StatusActivity.EXTRA_USER, primaryUser);
                startActivity(intent);
                finish();
            }
            else if (!isCancelled()) {
                Toast.makeText(IntentActivity.this, "ツイートの受信に失敗しました", Toast.LENGTH_SHORT).show();
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

    private interface AfterWork {
        void work(IntentActivity activity);
    }
}
