package shibafu.yukari.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.activity.base.ListYukariBase;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/08/09.
 */
public class IntentActivity extends ListYukariBase{

    private static final List<Pair<Pattern, AfterWork>> MATCHES;
    static {
        List<Pair<Pattern, AfterWork>> matchTemp = new ArrayList<>();
        matchTemp.add(new Pair<>(
                Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?$"),
                activity -> {
                    String id = activity.matchedWork.first.group(1);
                    TweetLoaderTask task = activity.new TweetLoaderTask();
                    task.executeParallel(Long.valueOf(id));
                }));
        matchTemp.add(new Pair<>(
                Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/?$"),
                activity -> {
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW,
                            activity.getIntent().getData(),
                            activity.getApplicationContext(),
                            ProfileActivity.class
                    );
                    activity.startActivity(intent);
                    activity.finish();
                }));
        matchTemp.add(new Pair<>(
                Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/intent/tweet"),
                activity -> {
                    Intent intent = new Intent(
                            Intent.ACTION_SEND,
                            activity.getIntent().getData(),
                            activity.getApplicationContext(),
                            TweetActivity.class
                    );
                    activity.startActivity(intent);
                    activity.finish();
                }));
        matchTemp.add(new Pair<>(
                Pattern.compile("^yukari://command/.+"),
                activity -> {
                    switch (activity.getIntent().getData().getLastPathSegment()) {
                        case "yukarin":
                            activity.startService(PostService.newIntent(activity, new TweetDraft.Builder().setText("＼ﾕｯｶﾘｰﾝ／").addWriter(activity.primaryUser).build()));
                            break;
                        default:
                            Toast.makeText(activity, "非対応タグです", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    activity.finish();
                }
        ));
        MATCHES = Collections.unmodifiableList(matchTemp);
    }

    protected Twitter twitter;
    protected AuthUserRecord primaryUser;

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
    public void onServiceConnected() {
        twitter = getTwitterService().getTwitter();
        primaryUser = getTwitterService().getPrimaryUser();

        matchedWork.second.work(IntentActivity.this);
    }

    @Override
    public void onServiceDisconnected() {

    }

    private class TweetLoaderTask extends ParallelAsyncTask<Long, Void, PreformedStatus> {

        private ProgressDialog currentDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            currentDialog = new ProgressDialog(IntentActivity.this);
            currentDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            currentDialog.setIndeterminate(true);
            currentDialog.setMessage("読み込み中...");
            currentDialog.setOnCancelListener(dialog -> cancel(true));
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
            }
            else if (!isCancelled()) {
                Toast.makeText(IntentActivity.this, "ツイートの受信に失敗しました", Toast.LENGTH_SHORT).show();
            }
            finish();
        }

        @Override
        protected void onCancelled() {
            if (currentDialog != null) {
                currentDialog.dismiss();
                currentDialog = null;
            }
            finish();
        }
    }

    private interface AfterWork {
        void work(IntentActivity activity);
    }
}
