package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import info.shibafu528.yukari.exvoice.BuildInfo;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.database.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/21.
 */
public class ConfigActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, InnerFragment.newInstance(getIntent().getStringExtra("category")))
                    .commit();
        }
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

    public static class InnerFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        public static InnerFragment newInstance(String category) {
            InnerFragment fragment = new InnerFragment();
            Bundle args = new Bundle();
            args.putString("category", category);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreatePreferencesFix(Bundle bundle, String s) {
            Bundle args = getArguments();
            String category = args.getString("category");
            if (TextUtils.isEmpty(category)) {
                addPreferencesFromResource(R.xml.pref);
            } else {
                switch (category) {
                    case "appearance":
                        getActivity().setTitle("表示設定");
                        addPreferencesFromResource(R.xml.pref_appearance);
                        break;
                    case "function":
                        getActivity().setTitle("機能設定");
                        addPreferencesFromResource(R.xml.pref_function);
                        break;
                    case "post":
                        getActivity().setTitle("投稿設定");
                        addPreferencesFromResource(R.xml.pref_post);
                        break;
                    case "filter":
                        getActivity().setTitle("フィルタ設定");
                        addPreferencesFromResource(R.xml.pref_filter);
                        break;
                    case "dialog":
                        getActivity().setTitle("操作確認設定");
                        addPreferencesFromResource(R.xml.pref_dialog);
                        break;
                    case "notify":
                        getActivity().setTitle("通知設定");
                        addPreferencesFromResource(R.xml.pref_notify);
                        break;
                    case "search":
                        getActivity().setTitle("検索設定");
                        addPreferencesFromResource(R.xml.pref_search);
                        break;
                    case "expert":
                        getActivity().setTitle("詳細設定");
                        addPreferencesFromResource(R.xml.pref_expert);
                        break;
                    case "plugin":
                        getActivity().setTitle("プラグインエンジン (実験中)");
                        addPreferencesFromResource(R.xml.pref_plugin);
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal category extra: " + category);
                }
            }
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            Bundle args = getArguments();
            String category = args.getString("category");
            if (TextUtils.isEmpty(category)) {
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

                findPreference("pref_about_feedback_mastodon").setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(getActivity(), TweetActivity.class);
                    String text = " #yukari4a @yukari4a@ertona.net";
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

            } else {
                switch (category) {
                    case "appearance":
                        findPreference("pref_boot_immersive").setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);

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
                        break;

                    case "notify":
                        findPreference("pref_notif_mention").setOnPreferenceClickListener(preference -> {
                            startActivity(NotificationPreferenceActivity.newIntent(requireActivity(), NotificationPreferenceActivity.NOTIFICATION_MENTION));
                            return false;
                        });
                        findPreference("pref_notif_rt").setOnPreferenceClickListener(preference -> {
                            startActivity(NotificationPreferenceActivity.newIntent(requireActivity(), NotificationPreferenceActivity.NOTIFICATION_RETWEET));
                            return false;
                        });
                        findPreference("pref_notif_fav").setOnPreferenceClickListener(preference -> {
                            startActivity(NotificationPreferenceActivity.newIntent(requireActivity(), NotificationPreferenceActivity.NOTIFICATION_FAVORITE));
                            return false;
                        });
                        findPreference("pref_notif_dm").setOnPreferenceClickListener(preference -> {
                            startActivity(NotificationPreferenceActivity.newIntent(requireActivity(), NotificationPreferenceActivity.NOTIFICATION_DIRECT_MESSAGE));
                            return false;
                        });
                        findPreference("pref_notif_respond").setOnPreferenceClickListener(preference -> {
                            startActivity(NotificationPreferenceActivity.newIntent(requireActivity(), NotificationPreferenceActivity.NOTIFICATION_RT_RESPOND));
                            return false;
                        });

                        findPreference("pref_notif_per_account_channel").setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
                        break;

                    case "expert":
                        findPreference("pref_export").setOnPreferenceClickListener(preference -> {
                            startActivity(new Intent(getActivity(), BackupActivity.class).putExtra(BackupActivity.EXTRA_MODE, BackupActivity.EXTRA_MODE_EXPORT));
                            return false;
                        });

                        findPreference("pref_import").setOnPreferenceClickListener(preference -> {
                            startActivity(new Intent(getActivity(), BackupActivity.class).putExtra(BackupActivity.EXTRA_MODE, BackupActivity.EXTRA_MODE_IMPORT));
                            return false;
                        });

                        Preference prefRepairNotificationChannel = findPreference("pref_repair_notification_channel");
                        prefRepairNotificationChannel.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
                        prefRepairNotificationChannel.setOnPreferenceClickListener(preference -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                TwitterService service = ((ConfigActivity) getActivity()).getTwitterService();
                                NotificationManager nm = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                                for (AuthUserRecord user : service.getUsers()) {
                                    service.createAccountNotificationChannels(nm, user, true);
                                }
                                Toast.makeText(getActivity(), "修復しました。", Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        });
                        break;

                    case "plugin": {
                        String summaryText = "libexvoice.so (" + BuildInfo.getABI() + ") Build: " + BuildInfo.getBuildDateTime() +
                                "\n  with " + BuildInfo.getMRubyDescription() + ",\n    " + BuildInfo.getMRubyCopyright();
                        // exvoice version
                        findPreference("pref_exvoice_version").setSummary(summaryText);
                        break;
                    }
                }
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("pref_notif_per_account_channel".equals(key)) {
                TwitterService service = ((ConfigActivity) getActivity()).getTwitterService();
                if (service != null) {
                    service.reloadUsers();
                }
            }
        }
    }
}
