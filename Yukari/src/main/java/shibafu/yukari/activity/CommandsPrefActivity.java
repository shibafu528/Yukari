package shibafu.yukari.activity;

import android.os.Bundle;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;

/**
 * Created by shibafu on 14/04/03.
 */
public class CommandsPrefActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, new InnerFragment())
                .commit();
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class InnerFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle paramBundle) {
            super.onCreate(paramBundle);
            addPreferencesFromResource(R.xml.commands);
        }
    }
}
