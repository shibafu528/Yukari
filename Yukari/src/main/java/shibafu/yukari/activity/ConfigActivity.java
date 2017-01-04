package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import com.github.machinarius.preferencefragment.PreferenceFragment;
import info.shibafu528.yukari.exvoice.BuildInfo;
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

            findPreference("pref_boot_immersive").setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);

            {
                String summaryText = "libexvoice.so (" + BuildInfo.getABI() + ") Build: " + BuildInfo.getBuildDateTime() +
                        "\n  with " + BuildInfo.getMRubyDescription() + ",\n    " + BuildInfo.getMRubyCopyright();
                // exvoice version
                findPreference("pref_exvoice_version").setSummary(summaryText);
            }

            {
                StringBuilder summaryText = new StringBuilder();
                PackageManager pm = getActivity().getPackageManager();
                // Yukari version
                try {
                    PackageInfo packageInfo = pm.getPackageInfo(getActivity().getPackageName(), 0);
                    summaryText.append("Version ").append(packageInfo.versionName);
                } catch (PackageManager.NameNotFoundException ignored) {}
                // Developer
                summaryText.append("\nDeveloped by @shibafu528");
                findPreference("pref_about_version").setSummary(summaryText.toString());
            }

            findPreference("pref_about_feedback").setOnPreferenceClickListener(preference -> {
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
            });

            findPreference("pref_prev_time").setOnPreferenceClickListener(preference -> {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                int selectedFlags = sp.getInt("pref_prev_time", 0);
                final boolean[] selectedStates = new boolean[24];
                for (int i = 0; i < 24; ++i) {
                    selectedStates[i] = (selectedFlags & 0x01) == 1;
                    selectedFlags >>>= 1;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle("サムネイル非表示にする時間帯")
                        .setPositiveButton("OK", (dialog, which) -> {
                            int selectedFlags1 = 0;
                            for (int i = 23; i >= 0; --i) {
                                selectedFlags1 <<= 1;
                                selectedFlags1 |= selectedStates[i] ? 1 : 0;
                            }
                            sp.edit().putInt("pref_prev_time", selectedFlags1).commit();
                        })
                        .setNegativeButton("キャンセル", (dialog, which) -> {
                        })
                        .setMultiChoiceItems(R.array.pref_prev_time_entries, selectedStates, (dialog, which, isChecked) -> {
                            selectedStates[which] = isChecked;
                        });
                builder.create().show();
                return true;
            });

            findPreference("pref_export").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), BackupActivity.class).putExtra(BackupActivity.EXTRA_MODE, BackupActivity.EXTRA_MODE_EXPORT));
                return false;
            });

            findPreference("pref_import").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), BackupActivity.class).putExtra(BackupActivity.EXTRA_MODE, BackupActivity.EXTRA_MODE_IMPORT));
                return false;
            });
        }
    }
}
