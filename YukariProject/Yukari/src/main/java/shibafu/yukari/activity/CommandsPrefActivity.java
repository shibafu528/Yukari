package shibafu.yukari.activity;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import shibafu.yukari.R;

/**
 * Created by shibafu on 14/04/03.
 */
public class CommandsPrefActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.YukariLightTheme);
                break;
            case "dark":
                setTheme(R.style.YukariDarkTheme);
                break;
        }
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.commands);
    }
}
