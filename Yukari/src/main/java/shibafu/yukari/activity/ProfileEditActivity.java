package shibafu.yukari.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/02/19.
 */
public class ProfileEditActivity extends ActionBarActivity {
    public static final String EXTRA_USER = "user";

    private TwitterService service;
    private boolean serviceBound = false;

    private AuthUserRecord userRecord;

    private ThrowableAsyncTask<Void, Void, ?> currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile_edit, menu);
        if (!getResources().getBoolean(R.bool.abc_split_action_bar_is_narrow)) {
            menu.removeItem(R.id.action_cancel);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.action_cancel:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }

    private void loadUserProfile(User user) {

    }

    private void onServiceConnected() {
        currentTask = new ThrowableTwitterAsyncTask<Void, User>() {
            @Override
            protected void showToast(String message) {
                Toast.makeText(ProfileEditActivity.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            protected ThrowableResult<User> doInBackground(Void... params) {
                try {
                    Twitter twitter = service.getTwitter();
                    twitter.setOAuthAccessToken(userRecord.getAccessToken());
                    return new ThrowableResult<User>(twitter.showUser(userRecord.NumericId));
                } catch (TwitterException e) {
                    e.printStackTrace();
                    return new ThrowableResult<User>(e);
                }
            }

            @Override
            protected void onPostExecute(ThrowableResult<User> result) {
                super.onPostExecute(result);
                if (!isErrored()) {
                    loadUserProfile(result.getResult());
                }
            }
        };
        currentTask.execute();
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            ProfileEditActivity.this.service = binder.getService();
            serviceBound = true;
            ProfileEditActivity.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
