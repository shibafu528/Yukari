package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.fragment.TweetListFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

public class MainActivity extends FragmentActivity {

    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();

    private static final int REQUEST_OAUTH = 1;
    private static final int REQUEST_FRIEND_CACHE = 2;

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isTouchTweet = false;
    private float tweetGestureYStart = 0;
    private float tweetGestureY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "[Yukari 起動エラー] ストレージエラー\nアプリの動作にはストレージが必須です", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        else if (!FontAsset.checkFontFileExt(this)) {
            Intent intent = new Intent(this, AssetExtractActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        //Twitterログインデータを読み込む
        twitter = TwitterUtil.getTwitterInstance(this);
        users = Arrays.asList(TwitterUtil.loadUserRecords(this));
        if (users.size() < 1) {
            startActivityForResult(new Intent(this, OAuthActivity.class), REQUEST_OAUTH);
        }
        else {
            addTab(users.get(0), "home:" + users.get(0).ScreenName, TweetListFragment.MODE_HOME);
        }

        FrameLayout area = (FrameLayout) findViewById(R.id.tweetgesture);
        area.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        tweetGestureYStart = event.getY();
                    case MotionEvent.ACTION_MOVE:
                        tweetGestureY = event.getY();
                        isTouchTweet = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (isTouchTweet && Math.abs(tweetGestureYStart - tweetGestureY) > 80) {
                            Intent intent = new Intent(MainActivity.this, TweetActivity.class);
                            intent.putExtra(TweetActivity.EXTRA_USER, users.get(0));
                            startActivity(intent);
                        }
                        break;
                }
                return true;
            }
        });
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

    private void addTab(AuthUserRecord user, String title, int mode) {
        TweetListFragment fragment = new TweetListFragment();
        Bundle b = new Bundle();
        b.putString(TweetListFragment.EXTRA_TITLE, title);
        b.putInt(TweetListFragment.EXTRA_MODE, mode);
        b.putSerializable(TweetListFragment.EXTRA_USER, user);
        fragment.setArguments(b);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame, fragment).commit();
        setTitle(title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_find_profile:
            {
                final EditText tvInput = new EditText(this);
                tvInput.setHint("@screen_name (@省略可)");

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("プロフィールを直接開く");
                builder.setView(tvInput);
                builder.setPositiveButton("開く", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String sn = tvInput.getText().toString();
                        if (sn.startsWith("@")) {
                            sn = sn.substring(1);
                        }

                        AsyncTask<String, Void, User> task = new AsyncTask<String, Void, User>() {
                            @Override
                            protected User doInBackground(String... params) {
                                try {
                                    User user = service.getTwitter().showUser(params[0]);
                                    return user;
                                } catch (TwitterException e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(User user) {
                                if (user != null) {
                                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                    intent.putExtra(ProfileActivity.EXTRA_TARGET, user.getId());
                                    startActivity(intent);
                                }
                                else {
                                    Toast.makeText(MainActivity.this, "ユーザー検索エラー", Toast.LENGTH_SHORT).show();
                                }
                            }
                        };
                        task.execute(sn);
                    }
                });
                builder.setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                AlertDialog ad = builder.create();
                ad.show();
                break;
            }
            case R.id.action_friend_cache:
                startActivityForResult(new Intent(this, SNPickerActivity.class), REQUEST_FRIEND_CACHE);
                break;
            case R.id.action_my_profile:
            {
                List<String> namesList = new ArrayList<String>();
                for (AuthUserRecord aur : users) {
                    namesList.add(aur.ScreenName);
                }
                final String[] names = namesList.toArray(new String[namesList.size()]);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("アカウントを選択")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                AsyncTask<String, Void, User> task = new AsyncTask<String, Void, User>() {
                                    @Override
                                    protected User doInBackground(String... params) {
                                        try {
                                            User user = service.getTwitter().showUser(params[0]);
                                            return user;
                                        } catch (TwitterException e) {
                                            e.printStackTrace();
                                        }
                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(User user) {
                                        if (user != null) {
                                            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                                            intent.putExtra(ProfileActivity.EXTRA_TARGET, user.getId());
                                            startActivity(intent);
                                        }
                                        else {
                                            Toast.makeText(MainActivity.this, "ユーザー検索エラー", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                };
                                task.execute(names[which]);
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.dismiss();
                            }
                        })
                        .create();
                dialog.show();
                break;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            switch (resultCode) {
                case RESULT_OK:
                    //認証情報をロードし差分を追加する
                    reloadUsers();
                    break;
                default:
                    break;
            }
        } else if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_FRIEND_CACHE:
                {
                    long id = data.getLongExtra(SNPickerActivity.EXTRA_USER_ID, -1);
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_TARGET, id);
                    startActivity(intent);
                    break;
                }
            }
        }
    }

    private void reloadUsers() {
        service.reloadUsers();
        AuthUserRecord[] newestList = TwitterUtil.loadUserRecords(this);
        for (AuthUserRecord aur : newestList) {
            if (!users.contains(aur)) {
                users.add(aur);
                addTab(aur, "home:" + aur.ScreenName, TweetListFragment.MODE_HOME);
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MainActivity.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
