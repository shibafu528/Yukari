package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;

/**
 * Created by Shibafu on 13/12/21.
 */
public class ConfigActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, new InnerFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class InnerFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle paramBundle) {
            super.onCreate(paramBundle);
            addPreferencesFromResource(R.xml.pref);
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            Preference accountManagePref = findPreference("pref_accounts");
            accountManagePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), AccountManageActivity.class));
                    return true;
                }
            });

            Preference immersivePref = findPreference("pref_boot_immersive");
            immersivePref.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);

            Preference aboutVersionPref = findPreference("pref_about_version");
            {
                String summaryText = "";
                PackageManager pm = getActivity().getPackageManager();
                try{
                    PackageInfo packageInfo = pm.getPackageInfo(getActivity().getPackageName(), 0);
                    summaryText += "Version " + packageInfo.versionName;
                }catch(PackageManager.NameNotFoundException e){
                    e.printStackTrace();
                }
                summaryText += "\nDeveloped by @shibafu528";
                aboutVersionPref.setSummary(summaryText);
            }
            aboutVersionPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), AboutActivity.class));
                    return true;
                }
            });
            findPreference("pref_about_licenses").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), LicenseActivity.class));
                    return true;
                }
            });
            findPreference("pref_about_feedback").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), TweetActivity.class);
                    String text = " #yukari4a @yukari4a";
                    PackageManager pm = getActivity().getPackageManager();
                    try {
                        PackageInfo packageInfo = pm.getPackageInfo(getActivity().getPackageName(), 0);
                        String[] versionText = packageInfo.versionName.split(" ");
                        if (versionText.length > 1) {
                            text += " //ver." + versionText[0];
                        }
                    } catch (PackageManager.NameNotFoundException e) {
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
                    final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    int selectedFlags = sp.getInt("pref_prev_time", 0);
                    final boolean[] selectedStates = new boolean[24];
                    for (int i = 0; i < 24; ++i) {
                        selectedStates[i] = (selectedFlags & 0x01) == 1;
                        selectedFlags >>>= 1;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                            .setTitle("サムネイル非表示にする時間帯")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    int selectedFlags = 0;
                                    for (int i = 23; i >= 0; --i) {
                                        selectedFlags <<= 1;
                                        selectedFlags |= selectedStates[i] ? 1 : 0;
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
                    startActivity(new Intent(getActivity(), MuteActivity.class));
                    return true;
                }
            });

            findPreference("pref_auto_mute").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), AutoMuteActivity.class));
                    return true;
                }
            });

            findPreference("pref_font").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), FontSelectorActivity.class));
                    return true;
                }
            });

            findPreference("pref_repair_bookmark").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), BookmarkRepairActivity.class));
                    return false;
                }
            });

            findPreference("pref_about_faq").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://bitbucket.org/shibafu528/yukari-for-android/wiki/%E5%AD%98%E5%9C%A8%E3%81%8C%E5%88%86%E3%81%8B%E3%82%8A%E3%81%AB%E3%81%8F%E3%81%84%E6%A9%9F%E8%83%BD")));
                    return false;
                }
            });
        }
    }
}
