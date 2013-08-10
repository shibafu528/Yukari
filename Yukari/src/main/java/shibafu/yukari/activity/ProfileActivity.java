package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import shibafu.yukari.R;
import shibafu.yukari.fragment.ProfileFragment;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileActivity extends FragmentActivity{

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        Intent intent = getIntent();

        ProfileFragment fragment = new ProfileFragment();
        Bundle b = new Bundle();
        b.putSerializable(ProfileFragment.EXTRA_USER, intent.getSerializableExtra(EXTRA_USER));
        b.putLong(ProfileFragment.EXTRA_TARGET, intent.getLongExtra(EXTRA_TARGET, -1));
        fragment.setArguments(b);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frame, fragment).commit();
    }
}
