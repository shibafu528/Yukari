package shibafu.yukari.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.IconLoaderTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
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
    private User user;

    private ThrowableAsyncTask<Void, Void, ?> currentTask;

    private ImageView ivIcon, ivHeader;
    private EditText etName, etLocation, etWeb, etBio;
    private LoadDialogFragment dialogFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        userRecord = (AuthUserRecord) getIntent().getSerializableExtra(EXTRA_USER);

        ivIcon = (ImageView) findViewById(R.id.ivProfileIcon);
        ivHeader = (ImageView) findViewById(R.id.ivProfileHeader);
        etName = (EditText) findViewById(R.id.etProfileName);
        etLocation = (EditText) findViewById(R.id.etProfileLocation);
        etWeb = (EditText) findViewById(R.id.etProfileWeb);
        etBio = (EditText) findViewById(R.id.etProfileBio);

        findViewById(R.id.btnUndoIcon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ivIcon.setTag(user.getBiggerProfileImageURLHttps());
                new IconLoaderTask(ProfileEditActivity.this, ivIcon).executeIf(user.getBiggerProfileImageURLHttps());
                Toast.makeText(ProfileEditActivity.this, "Undo!", Toast.LENGTH_SHORT).show();
            }
        });
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
    protected void onResume() {
        super.onResume();
        if (user == null) {
            dialogFragment = new LoadDialogFragment();
            dialogFragment.show(getSupportFragmentManager(), "load");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        if (dialogFragment != null) {
            dialogFragment.dismiss();
            dialogFragment = null;
        }
    }

    private void onCancelledLoading() {
        dialogFragment = null;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        finish();
    }

    private void loadUserProfile(User user) {
        this.user = user;

        ivIcon.setTag(user.getBiggerProfileImageURLHttps());
        new IconLoaderTask(this, ivIcon).executeIf(user.getBiggerProfileImageURLHttps());

        ivHeader.setTag(user.getProfileBannerMobileURL());
        new ImageLoaderTask(this, ivHeader).executeIf(user.getProfileBannerMobileURL());

        etName.setText(user.getName());
        etLocation.setText(user.getLocation());
        etWeb.setText(user.getURLEntity().getExpandedURL());
        etBio.setText(user.getDescription());
    }

    private void onServiceConnected() {
        if (user == null) {
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
                    if (dialogFragment != null) {
                        dialogFragment.dismiss();
                        dialogFragment = null;
                    }
                    if (!isErrored() && !isCancelled()) {
                        loadUserProfile(result.getResult());
                    }
                }
            };
            currentTask.execute();
        }
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

    public static class LoadDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog pd = new ProgressDialog(getActivity());
            pd.setMessage("読み込み中...");
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setIndeterminate(true);
            return pd;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            ((ProfileEditActivity)getActivity()).onCancelledLoading();
        }
    }
}
