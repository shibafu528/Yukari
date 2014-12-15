package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/12/21.
 */
public class ConfigActivity extends PreferenceActivity {

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    setTheme(android.R.style.Theme_Light);
                } else {
                    setTheme(R.style.YukariLightTheme);
                }
                break;
            case "dark":
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    setTheme(android.R.style.Theme_Black);
                } else {
                    setTheme(R.style.YukariDarkTheme);
                }
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);

        Preference accountManagePref = findPreference("pref_accounts");
        accountManagePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, AccountManageActivity.class));
                return true;
            }
        });

        Preference immersivePref = findPreference("pref_boot_immersive");
        immersivePref.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);

        Preference aboutVersionPref = findPreference("pref_about_version");
        {
            String summaryText = "";
            PackageManager pm = getPackageManager();
            try{
                PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
                summaryText += "Version " + packageInfo.versionName;
            }catch(PackageManager.NameNotFoundException e){
                e.printStackTrace();
            }
            summaryText += "\nDeveloped by @shibafu528";
            aboutVersionPref.setSummary(summaryText);
        }
        findPreference("pref_about_licenses").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, LicenseActivity.class));
                return true;
            }
        });
        findPreference("pref_about_feedback").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(ConfigActivity.this, TweetActivity.class);
                String text = " #yukari4a @yukari4a";
                PackageManager pm = getPackageManager();
                try {
                    PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
                    String[] versionText = packageInfo.versionName.split(" ");
                    if (versionText != null && versionText.length > 1) {
                        text += " //ver." + versionText[0];
                    }
                } catch (PackageManager.NameNotFoundException e){
                    e.printStackTrace();
                }
                intent.putExtra(TweetActivity.EXTRA_TEXT, text);
                startActivity(intent);
                return true;
            }
        });

        Preference prevTimePref = findPreference("pref_prev_time");
        prevTimePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ConfigActivity.this);
                int selectedFlags = sp.getInt("pref_prev_time", 0);
                final boolean[] selectedStates = new boolean[24];
                for (int i = 0; i < 24; ++i) {
                    selectedStates[i] = (selectedFlags & 0x01) == 1;
                    selectedFlags >>>= 1;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this)
                        .setTitle("サムネイル非表示にする時間帯")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int selectedFlags = 0;
                                for (int i = 23; i >= 0; --i) {
                                    selectedFlags <<= 1;
                                    selectedFlags |= selectedStates[i]?1:0;
                                }
                                sp.edit().putInt("pref_prev_time", selectedFlags).commit();
                            }
                        })
                        .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setMultiChoiceItems(R.array.pref_prev_time_entries, selectedStates, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                selectedStates[which] = isChecked;
                            }
                        });
                builder.create().show();
                return true;
            }
        });

        findPreference("pref_mute").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, MuteActivity.class));
                return true;
            }
        });

        findPreference("pref_auto_mute").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, AutoMuteActivity.class));
                return true;
            }
        });

        findPreference("pref_font").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, FontSelectorActivity.class));
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
