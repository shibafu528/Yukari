package shibafu.yukari.activity;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import shibafu.yukari.R;

/**
 * Created by shibafu on 14/04/03.
 */
public class CommandsPrefActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.commands);
    }
}
