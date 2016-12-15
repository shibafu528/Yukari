package shibafu.yukari.util;

import android.app.Activity;
import android.preference.PreferenceManager;

import shibafu.yukari.R;

/**
 * Created by shibafu on 15/03/26.
 */
public class ThemeUtil {
    public static void setActivityTheme(Activity activity) {
        switch (PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_theme", "light")) {
            case "light":
                activity.setTheme(R.style.ColorsTheme_Light);
                break;
            case "dark":
                activity.setTheme(R.style.ColorsTheme_Dark);
                break;
            case "zunko":
                activity.setTheme(R.style.ColorsTheme_Zunko);
                break;
            case "maki":
                activity.setTheme(R.style.ColorsTheme_Maki);
                break;
            case "aoi":
                activity.setTheme(R.style.ColorsTheme_Aoi);
                break;
            case "akane":
                activity.setTheme(R.style.ColorsTheme_Akane);
                break;
        }
    }

    public static void setDialogTheme(Activity activity) {
        switch (PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_theme", "light")) {
            case "light":
                activity.setTheme(R.style.ColorsTheme_Light_Dialog);
                break;
            case "dark":
                activity.setTheme(R.style.ColorsTheme_Dark_Dialog);
                break;
            case "zunko":
                activity.setTheme(R.style.ColorsTheme_Zunko_Dialog);
                break;
            case "maki":
                activity.setTheme(R.style.ColorsTheme_Maki_Dialog);
                break;
            case "aoi":
                activity.setTheme(R.style.ColorsTheme_Aoi_Dialog);
                break;
            case "akane":
                activity.setTheme(R.style.ColorsTheme_Akane_Dialog);
                break;
        }
    }
}
