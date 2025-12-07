package shibafu.yukari.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import android.util.Pair;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.Provider;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.service.PostService;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.entity.TwitterStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Shibafu on 13/08/09.
 */
public class IntentActivity extends ActionBarYukariBase {

    private static final List<Pair<Pattern, AfterWork>> MATCHES;
    static {
        List<Pair<Pattern, AfterWork>> matchTemp = new ArrayList<>();
        matchTemp.add(new Pair<>(
                Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?(?:\\?.+)?$"),
                activity -> {
                    String id = activity.matchedWork.first.group(1);
                    TweetLoaderTask task = activity.new TweetLoaderTask();
                    task.executeParallel(Long.valueOf(id));
                }));
        matchTemp.add(new Pair<>(
                Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/?(?:\\?.+)?$"),
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
                            StatusDraft draft = new StatusDraft();
                            draft.setWriters(activity.twitterUser.toSingleList());
                            draft.setText("＼ﾕｯｶﾘｰﾝ／");
                            ContextCompat.startForegroundService(activity, PostService.newIntent(activity, draft));
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
    protected AuthUserRecord twitterUser;

    private Pair<Matcher, AfterWork> matchedWork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light").endsWith("dark")) {
            setTheme(R.style.ColorsTheme_Dark_Translucent_FullTransparent);
        } else {
            setTheme(R.style.ColorsTheme_Light_Translucent_FullTransparent);
        }
        super.onCreate(savedInstanceState, true);

        String url = getIntent().getDataString();
        for (Pair<Pattern, AfterWork> pair : MATCHES) {
            Matcher matcher = pair.first.matcher(url);
            if (matcher.find()) {
                matchedWork = new Pair<>(matcher, pair.second);
                break;
            }
        }
        if (matchedWork == null) {
            Toast.makeText(getApplicationContext(), "Yukariでは開けないURLです", Toast.LENGTH_SHORT).show();
            Intent copy = new Intent(getIntent());
            copy.setComponent(null);
            startActivity(Intent.createChooser(copy, null));
            finish();
        }
    }

    @Override
    public void onServiceConnected() {
        App app = App.getInstance(this);
        AccountManager am = app.getAccountManager();

        // 使えそうなTwitterアカウントを探す
        twitterUser = am.getPrimaryUser();
        if (twitterUser == null || twitterUser.Provider.getApiType() != Provider.API_TWITTER) {
            for (AuthUserRecord user : am.getUsers()) {
                if (user.InternalId != twitterUser.InternalId && user.Provider.getApiType() == Provider.API_TWITTER) {
                    twitterUser = user;
                    break;
                }
            }
        }
        twitter = TwitterUtil.getTwitter(this, twitterUser);

        matchedWork.second.work(IntentActivity.this);
    }

    private class TweetLoaderTask extends ParallelAsyncTask<Long, Void, TwitterStatus> {

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
        protected TwitterStatus doInBackground(Long... params) {
            if (twitter == null) return null;
            try {
                twitter4j.Status status = twitter.showStatus(params[0]);
                TwitterStatus ts = new TwitterStatus(status, twitterUser);
                if (isCancelled()) return null;
                return ts;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(TwitterStatus status) {
            if (currentDialog != null) {
                currentDialog.dismiss();
                currentDialog = null;
            }

            if (status != null) {
                Intent intent = new Intent(IntentActivity.this, StatusActivity.class);
                intent.putExtra(StatusActivity.EXTRA_STATUS, status);
                intent.putExtra(StatusActivity.EXTRA_USER, twitterUser);
                startActivity(intent);
            }
            else if (!isCancelled()) {
                Toast.makeText(IntentActivity.this, "投稿の取得に失敗しました", Toast.LENGTH_SHORT).show();
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
