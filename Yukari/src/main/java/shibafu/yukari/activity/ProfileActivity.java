package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.fragment.ProfileFragment;
import shibafu.yukari.fragment.tabcontent.TwitterListFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.ThemeUtil;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    public static Intent newIntent(@NonNull Context context, @Nullable AuthUserRecord userRecord, long targetId) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra(EXTRA_USER, userRecord);
        intent.putExtra(EXTRA_TARGET, targetId);
        return intent;
    }

    public static Intent newIntent(@NonNull Context context, @Nullable AuthUserRecord userRecord, @NonNull Uri target) {
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
            Fragment f = manager.findFragmentByTag("contain");
            if (manager.getBackStackEntryCount() > 0 && f instanceof TwitterListFragment) {
                actionBar.show();
            }
            else {
                actionBar.hide();
            }
        });

        if (savedInstanceState == null) {
            actionBar.hide();

            Intent intent = getIntent();
            if (intent.getData() != null && intent.getData().getLastPathSegment() == null) {
                Toast.makeText(this, "エラー: 無効なユーザ指定です", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            ProfileFragment fragment = new ProfileFragment();
            Bundle b = new Bundle();
            b.putSerializable(ProfileFragment.EXTRA_USER, intent.getSerializableExtra(EXTRA_USER));
            b.putLong(ProfileFragment.EXTRA_TARGET, intent.getLongExtra(EXTRA_TARGET, -1));
            b.putParcelable("data", intent.getData());
            fragment.setArguments(b);

            FragmentTransaction ft = manager.beginTransaction();
            ft.replace(R.id.frame, fragment, "contain").commit();
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
