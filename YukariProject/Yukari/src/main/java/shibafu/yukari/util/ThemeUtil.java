package shibafu.yukari.util;

import android.app.Activity;
import android.preference.PreferenceManager;

import shibafu.yukari.af2015.R;

/**
 * Created by shibafu on 15/03/26.
 */
public class ThemeUtil {
    public static void setActivityTheme(Activity activity) {
        switch (PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_theme", "light")) {
            case "light":
                activity.setTheme(R.style.YukariLightTheme);
                break;
            case "dark":
                activity.setTheme(R.style.YukariDarkTheme);
                break;
            case "zunko":
                activity.setTheme(R.style.ColorsTheme_Zunko);
                break;
            case "maki":
                activity.setTheme(R.style.ColorsTheme_Maki);
                break;
        }
    }

    public static void setDialogTheme(Activity activity) {
        switch (PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_theme", "light")) {
            case "light":
                activity.setTheme(R.style.YukariLightDialogTheme);
                break;
            case "dark":
                activity.setTheme(R.style.YukariDarkDialogTheme);
                break;
            case "zunko":
                activity.setTheme(R.style.ColorsTheme_Zunko_Dialog);
                break;
            case "maki":
                activity.setTheme(R.style.ColorsTheme_Maki_Dialog);
                break;
        }
    }
}
