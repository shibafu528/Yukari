package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.database.Provider;
import shibafu.yukari.fragment.MastodonProfileFragment;
import shibafu.yukari.fragment.ProfileFragment;
import shibafu.yukari.fragment.base.AbstractPaginateListFragment;
import shibafu.yukari.fragment.base.AbstractUserListFragment;
import shibafu.yukari.fragment.tabcontent.TimelineFragment;
import shibafu.yukari.fragment.tabcontent.TwitterListTimelineFragment;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.util.ThemeUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileActivity extends AppCompatActivity {
    private static final String LOG_TAG = ProfileActivity.class.getSimpleName();

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    public static final String FRAGMENT_TAG_CONTENT = "content";

    public static Intent newIntent(@NonNull Context context, @Nullable AuthUserRecord userRecord, @NonNull Uri target) {
        // userRecord引数は将来的にNonNullにする。大半の呼出では指定されているはずだが、しばらくの間は調査する。
        if (userRecord == null) {
            StringWriter sw = new StringWriter();
            sw.write("userRecordが省略されています。Yukari 2.1以降では非推奨です。\n");
            new Throwable().printStackTrace(new PrintWriter(sw));
            Log.w(LOG_TAG, sw.toString());
        }

        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra(EXTRA_USER, userRecord);
        intent.setData(target);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        final FragmentManager manager = getSupportFragmentManager();
        manager.addOnBackStackChangedListener(() -> {
            Fragment f = manager.findFragmentByTag(FRAGMENT_TAG_CONTENT);
            if (manager.getBackStackEntryCount() > 0 && (
                    f instanceof TimelineFragment ||
                            f instanceof TwitterListTimelineFragment ||
                            f instanceof AbstractUserListFragment ||
                            f instanceof AbstractPaginateListFragment
            )) {
                actionBar.show();
            }
            else {
                actionBar.hide();
            }
        });

        if (savedInstanceState == null) {
            actionBar.hide();

            Intent intent = getIntent();
            Uri uri = intent.getData();
            if (uri == null) {
                Toast.makeText(this, "エラー: 無効なユーザ指定です", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            final AuthUserRecord user = (AuthUserRecord) intent.getSerializableExtra(EXTRA_USER);

            if ("twitter.com".equals(uri.getHost()) && !TextUtils.isEmpty(uri.getLastPathSegment())) {
                ProfileFragment fragment = new ProfileFragment();
                Bundle b = new Bundle();
                b.putSerializable(ProfileFragment.EXTRA_USER, user);
                b.putLong(ProfileFragment.EXTRA_TARGET, intent.getLongExtra(EXTRA_TARGET, -1));
                b.putParcelable("data", intent.getData());
                fragment.setArguments(b);

                FragmentTransaction ft = manager.beginTransaction();
                ft.replace(R.id.frame, fragment, FRAGMENT_TAG_CONTENT).commit();
            } else if (user != null && user.Provider.getApiType() == Provider.API_MASTODON) {
                // Mastodonアカウントで開いた場合、Mastodonのユーザプロフィールとして開いてみる
                MastodonProfileFragment fragment = MastodonProfileFragment.Companion.newInstance(user, intent.getData());
                manager.beginTransaction()
                        .replace(R.id.frame, fragment, FRAGMENT_TAG_CONTENT)
                        .commit();
            }  else {
                // 非対応ホストの場合は他のアプリに頑張ってもらう
                Intent newIntent = new Intent(Intent.ACTION_VIEW, uri);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(newIntent);
                finish();
            }
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                actionBar.hide();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStackImmediate();
                } else {
                    finish();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
