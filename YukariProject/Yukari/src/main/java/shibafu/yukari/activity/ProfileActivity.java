package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.fragment.ProfileFragment;
import shibafu.yukari.fragment.tabcontent.TwitterListFragment;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileActivity extends ActionBarActivity{

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        final FragmentManager manager = getSupportFragmentManager();
        manager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                Fragment f = manager.findFragmentByTag("contain");
                if (manager.getBackStackEntryCount() > 0 && f instanceof TwitterListFragment) {
                    actionBar.show();
                }
                else {
                    actionBar.hide();
                }
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
