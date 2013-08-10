package shibafu.yukari.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import shibafu.yukari.R;
import shibafu.yukari.fragment.ProfileFragment;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileActivity extends FragmentActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frame, new ProfileFragment()).commit();
    }
}
