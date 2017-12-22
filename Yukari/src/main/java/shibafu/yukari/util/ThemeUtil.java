package shibafu.yukari.util;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import shibafu.yukari.R;

/**
 * Created by shibafu on 15/03/26.
 */
public class ThemeUtil {
    public static int getActivityThemeId(Context context) {
        switch (PreferenceManager.getDefaultSharedPreferences(context).getString("pref_theme", "light")) {
            case "light":
                return R.style.ColorsTheme_Light;
            case "dark":
                return R.style.ColorsTheme_Dark;
            case "akari":
                return R.style.ColorsTheme_Akari;
            case "akari_dark":
                return R.style.ColorsTheme_Akari_Dark;
            case "zunko":
                return R.style.ColorsTheme_Zunko;
            case "zunko_dark":
                return R.style.ColorsTheme_Zunko_Dark;
            case "maki":
                return R.style.ColorsTheme_Maki;
            case "maki_dark":
                return R.style.ColorsTheme_Maki_Dark;
            case "aoi":
                return R.style.ColorsTheme_Aoi;
            case "aoi_dark":
                return R.style.ColorsTheme_Aoi_Dark;
            case "akane":
                return R.style.ColorsTheme_Akane;
            case "akane_dark":
                return R.style.ColorsTheme_Akane_Dark;
        }
        throw new RuntimeException("Invalid theme config value.");
    }
    
    public static void setActivityTheme(Activity activity) {
        activity.setTheme(getActivityThemeId(activity));
    }

    public static void setDialogTheme(Activity activity) {
        switch (PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_theme", "light")) {
            case "light":
                activity.setTheme(R.style.ColorsTheme_Light_Dialog);
                break;
            case "dark":
                activity.setTheme(R.style.ColorsTheme_Dark_Dialog);
                break;
            case "akari":
                activity.setTheme(R.style.ColorsTheme_Akari_Dialog);
                break;
            case "akari_dark":
                activity.setTheme(R.style.ColorsTheme_Akari_Dark_Dialog);
                break;
            case "zunko":
                activity.setTheme(R.style.ColorsTheme_Zunko_Dialog);
                break;
            case "zunko_dark":
                activity.setTheme(R.style.ColorsTheme_Zunko_Dark_Dialog);
                break;
            case "maki":
                activity.setTheme(R.style.ColorsTheme_Maki_Dialog);
                break;
            case "maki_dark":
                activity.setTheme(R.style.ColorsTheme_Maki_Dark_Dialog);
                break;
            case "aoi":
                activity.setTheme(R.style.ColorsTheme_Aoi_Dialog);
                break;
            case "aoi_dark":
                activity.setTheme(R.style.ColorsTheme_Aoi_Dark_Dialog);
                break;
            case "akane":
                activity.setTheme(R.style.ColorsTheme_Akane_Dialog);
                break;
            case "akane_dark":
                activity.setTheme(R.style.ColorsTheme_Akane_Dark_Dialog);
                break;
        }
    }
}
