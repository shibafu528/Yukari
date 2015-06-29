package shibafu.yukari.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.PostProgressDialogFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/02/19.
 */
public class ProfileEditActivity extends ActionBarYukariBase {
    public static final String EXTRA_USER = "user";

    private static final int REQUEST_GALLERY = 0;
    private static final int REQUEST_CROP = 1;

    private AuthUserRecord userRecord;
    private User user;

    private Bitmap trimmedIcon;

    private ThrowableAsyncTask<Void, Void, ?> currentTask;

    private ImageView ivIcon, ivHeader;
    private EditText etName, etLocation, etWeb, etBio;
    private File tempFile;

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

        findViewById(R.id.btnChooseIcon).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_GALLERY);
        });

        findViewById(R.id.btnUndoIcon).setOnClickListener(v -> {
            if (trimmedIcon != null) {
                trimmedIcon.recycle();
                trimmedIcon = null;
            }
            ImageLoaderTask.loadProfileIcon(getApplicationContext(), ivIcon, user.getBiggerProfileImageURLHttps());
            Toast.makeText(ProfileEditActivity.this, "Undo!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // トリミング処理このへん参考にした
        // http://goodspeed.hatenablog.com/entry/20120313/1331623333
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            Intent intent = new Intent("com.android.camera.action.CROP");
            try {
                tempFile = new File(getExternalCacheDir(), "icon_temp.png");
                intent.setDataAndType(uri, "image/*");
                intent.putExtra("outputX", 512);
                intent.putExtra("outputY", 512);
                intent.putExtra("aspectX", 1);
                intent.putExtra("aspectY", 1);
                intent.putExtra("scale", true);
                intent.putExtra("noFaceDetection", true);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile));
                intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.name());

                startActivityForResult(intent, REQUEST_CROP);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "トリミングの呼び出しに失敗しました", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_CROP && resultCode == RESULT_OK) {
            try {
                InputStream is = new FileInputStream(tempFile);
                trimmedIcon = BitmapFactory.decodeStream(is);
                ivIcon.setImageBitmap(trimmedIcon);
            } catch (FileNotFoundException | NullPointerException e) {
                e.printStackTrace();
                Toast.makeText(this, "画像のロードに失敗しました", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile_edit, menu);
//        if (!getResources().getBoolean(R.bool.abc_split_action_bar_is_narrow)) {
//            menu.removeItem(R.id.action_cancel);
//        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.action_cancel:
                finish();
                return true;
            case R.id.action_accept:
                new ThrowableTwitterAsyncTask<Void, Void>() {
                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(ProfileEditActivity.this, message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected ThrowableResult<Void> doInBackground(Void... params) {
                        try {
                            Twitter twitter = getTwitterService().getTwitter();
                            twitter.setOAuthAccessToken(userRecord.getAccessToken());
                            twitter.updateProfile(
                                    etName.getText().toString(),
                                    etWeb.getText().toString(),
                                    etLocation.getText().toString(),
                                    etBio.getText().toString());
                            //新しいアイコンよー、それー
                            if (trimmedIcon != null && tempFile != null) {
                                twitter.updateProfileImage(tempFile);
                            }
                            return new ThrowableResult<>((Void) null);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        PostProgressDialogFragment dialogFragment = PostProgressDialogFragment.newInstance();
                        dialogFragment.show(getSupportFragmentManager(), "post");
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<Void> result) {
                        super.onPostExecute(result);
                        Fragment dialogFragment = getSupportFragmentManager().findFragmentByTag("post");
                        if (dialogFragment != null && dialogFragment instanceof PostProgressDialogFragment) {
                            ((PostProgressDialogFragment) dialogFragment).dismiss();
                        }
                        if (!isErrored()) {
                            Toast.makeText(ProfileEditActivity.this, "プロフィールを更新しました", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                }.execute();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onCancelledLoading() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        finish();
    }

    private void loadUserProfile(User user) {
        this.user = user;

        if (trimmedIcon != null) {
            trimmedIcon.recycle();
            trimmedIcon = null;
        }
        ImageLoaderTask.loadProfileIcon(getApplicationContext(), ivIcon, user.getBiggerProfileImageURLHttps());

        if (user.getProfileBannerMobileURL() != null) {
            ImageLoaderTask.loadBitmap(getApplicationContext(), ivHeader, user.getProfileBannerMobileURL());
        }

        etName.setText(user.getName());
        etLocation.setText(user.getLocation());
        etWeb.setText(user.getURLEntity().getExpandedURL());
        etBio.setText(user.getDescription());
    }

    @Override
    public void onServiceConnected() {
        if (user == null) {
            currentTask = new ThrowableTwitterAsyncTask<Void, User>() {
                @Override
                protected void showToast(String message) {
                    Toast.makeText(ProfileEditActivity.this, message, Toast.LENGTH_LONG).show();
                }

                @Override
                protected ThrowableResult<User> doInBackground(Void... params) {
                    try {
                        Twitter twitter = getTwitterService().getTwitter();
                        twitter.setOAuthAccessToken(userRecord.getAccessToken());
                        return new ThrowableResult<>(twitter.showUser(userRecord.NumericId));
                    } catch (TwitterException e) {
                        e.printStackTrace();
                        return new ThrowableResult<>(e);
                    }
                }

                @Override
                protected void onPreExecute() {
                    LoadDialogFragment dialogFragment = new LoadDialogFragment();
                    dialogFragment.show(getSupportFragmentManager(), "load");
                }

                @Override
                protected void onPostExecute(ThrowableResult<User> result) {
                    super.onPostExecute(result);
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag("load");
                    if (fragment != null && fragment instanceof LoadDialogFragment) {
                        ((LoadDialogFragment) fragment).dismiss();
                    }
                    if (!isErrored() && !isCancelled()) {
                        loadUserProfile(result.getResult());
                    }
                    currentTask = null;
                }
            };
            currentTask.execute();
        }
    }

    @Override
    public void onServiceDisconnected() {

    }

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
