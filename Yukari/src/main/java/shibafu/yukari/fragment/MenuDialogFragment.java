package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.res.ResourcesCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.google.gson.Gson;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.ConfigActivity;
import shibafu.yukari.activity.IntentChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.plugin.UserPluginActivity;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.util.AttrUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by Shibafu on 13/12/16.
 */
public class MenuDialogFragment extends DialogFragment {

    private static final int REQUEST_PROFILE = 1;
    private static final int REQUEST_TWILOG = 2;
    private static final int REQUEST_FAVSTAR = 3;
    private static final int REQUEST_ACLOG = 4;
    private static final int REQUEST_ACCOUNT = 5;

    private static final List<Integer> REQUEST_PLUGIN_CHOOSE = Collections.unmodifiableList(Arrays.asList(16, 17, 18));
    private static final List<Integer> REQUEST_PLUGIN_EXEC = Collections.unmodifiableList(Arrays.asList(32, 33, 34));

    private static final int ACCOUNT_ICON_DIP = 32;

    @BindViews({R.id.llMenuTwilog, R.id.llMenuFavstar, R.id.llMenuAclog})
    List<View> pluginViews;

    private MenuPlugin[] plugins = new MenuPlugin[3];
    private static final String[] DEFAULT_PLUGINS = {
            UserPluginActivity.TO_TWILOG,
            null,
            null
    };

    private LinearLayout llActiveAccounts;
    private ArrayList<AuthUserRecord> activeAccounts;

    private ImageView keepScreenOnImage;
    private Unbinder unbinder;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        for (int i = 0; i < 3; ++i) {
            String json = sp.getString("menu_plugin_" + i, "");
            if (TextUtils.isEmpty(json)) {
                if (DEFAULT_PLUGINS[i] == null) continue;

                try {
                    ActivityInfo info = context.getPackageManager()
                            .getActivityInfo(new ComponentName(context, DEFAULT_PLUGINS[i]), 0);
                    plugins[i] = new MenuPlugin(context, info);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                plugins[i] = new Gson().fromJson(json, MenuPlugin.class);
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light").endsWith("dark")) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
        }

        dialog.setContentView(inflateView(getActivity().getLayoutInflater()));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = (int) (0.95f * metrics.widthPixels);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = dialogWidth;
        dialog.getWindow().setAttributes(lp);

        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity activity = (MainActivity) getActivity();
        if (activity.isKeepScreenOn()) {
            keepScreenOnImage.setImageResource(R.drawable.ic_always_light_on);
        }
        if (activity.isTwitterServiceBound()) {
            activeAccounts = activity.getTwitterService().getActiveUsers();
            createAccountIconView();
        } else {
            class AccountsLoader extends SimpleAsyncTask {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity.isTwitterServiceBound()) {
                        activeAccounts = activity.getTwitterService().getActiveUsers();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    if (activeAccounts != null) {
                        createAccountIconView();
                    } else {
                        new AccountsLoader().executeParallel();
                    }
                }
            }
            new AccountsLoader().executeParallel();
        }
    }

    private void createAccountIconView() {
        llActiveAccounts.removeAllViewsInLayout();
        final int iconSize = (int) (getResources().getDisplayMetrics().density * ACCOUNT_ICON_DIP);
        for (AuthUserRecord user : activeAccounts) {
            ImageView iv = new ImageView(getActivity());
            iv.setFocusable(false);
            iv.setClickable(false);
            ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), iv, user.ProfileImageUrl);
            llActiveAccounts.addView(iv, iconSize, iconSize);
        }
    }

    private View inflateView(LayoutInflater inflater) {
        View v = inflater.inflate(R.layout.dialog_menu, null);

        llActiveAccounts = (LinearLayout) v.findViewById(R.id.llMenuAccounts);
        v.findViewById(R.id.llMenuAccountParent).setOnClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS, activeAccounts);
            startActivityForResult(intent, REQUEST_ACCOUNT);
        });

        View profileMenu = v.findViewById(R.id.llMenuProfile);
        profileMenu.setOnClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            startActivityForResult(intent, REQUEST_PROFILE);
        });

        View exitMenu = v.findViewById(R.id.llMenuExit);
        exitMenu.setOnClickListener(v1 -> {
            dismiss();
            ((MainActivity)getActivity()).showExitDialog();
        });

        keepScreenOnImage = (ImageView) v.findViewById(R.id.ivMenuSleepIcon);

        View keepScreenOnMenu = v.findViewById(R.id.llMenuSleep);
        keepScreenOnMenu.setOnClickListener(v1 -> {
            MainActivity activity = ((MainActivity) getActivity());
            if (activity.isKeepScreenOn()) {
                activity.setKeepScreenOn(false);
                keepScreenOnImage.setImageResource(AttrUtil.resolveAttribute(getDialog().getContext().getTheme(), R.attr.menuBacklightDrawable));
            } else {
                activity.setKeepScreenOn(true);
                keepScreenOnImage.setImageResource(R.drawable.ic_always_light_on);
            }
        });
        keepScreenOnMenu.setOnLongClickListener(v1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                MainActivity activity = ((MainActivity) getActivity());
                activity.setImmersive(!activity.isImmersive());
                Toast.makeText(getActivity(), "表示域拡張を" + ((activity.isImmersive()) ? "有効" : "無効") + "にしました", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        View bookmarkMenu = v.findViewById(R.id.llMenuBookmark);
        bookmarkMenu.setOnClickListener(view -> {
            dismiss();
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_BOOKMARK);
            startActivity(intent);
        });

        View configMenu = v.findViewById(R.id.llMenuConfig);
        configMenu.setOnClickListener(v1 -> {
            dismiss();
            startActivity(new Intent(getActivity(), ConfigActivity.class));
        });

        ImageButton ibReconnect = (ImageButton) v.findViewById(R.id.ibMenuReconnect);
        ibReconnect.setOnClickListener(v1 -> {
            Toast.makeText(getActivity(), "再接続します...", Toast.LENGTH_LONG).show();
            dismiss();
            ((TwitterServiceDelegate)getActivity()).getTwitterService().getStatusManager().reconnectAsync();
        });

        unbinder = ButterKnife.bind(this, v);
        ButterKnife.apply(pluginViews, (ButterKnife.Action<? super View>) (view, index) -> {
            view.setOnClickListener(v1 -> {
                if (plugins[index] == null) {
                    return;
                }
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_PLUGIN_EXEC.get(index));
            });
            view.setOnLongClickListener(v1 -> {
                Intent filter = new Intent("jp.r246.twicca.ACTION_SHOW_USER");
                filter.addCategory("jp.r246.twicca.category.OWNER");
                Intent chooser = new Intent(getActivity(), IntentChooserActivity.class);
                chooser.putExtra(IntentChooserActivity.EXTRA_FILTER, filter);
                startActivityForResult(chooser, REQUEST_PLUGIN_CHOOSE.get(index));
                return true;
            });
        });

        updatePlugin(0, plugins[0]);
        updatePlugin(1, plugins[1]);
        updatePlugin(2, plugins[2]);

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    private void updatePlugin(int index, MenuPlugin plugin) {
        if (plugins[index] != plugin) {
            String json = new Gson().toJson(plugin);
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putString("menu_plugin_" + index, json)
                    .apply();
        }
        plugins[index] = plugin;
        View v = pluginViews.get(index);
        TextView label = (TextView) v.findViewById(R.id.tvMenuPlugin);
        ImageView icon = (ImageView) v.findViewById(R.id.ivMenuPlugin);
        if (plugin == null) {
            label.setText("");
            icon.setImageDrawable(null);
        } else {
            label.setText(plugin.getShortLabel());
            try {
                Resources res = getActivity().getPackageManager().getResourcesForActivity(plugin.getComponentName());
                if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light").endsWith("dark")) {
                    icon.setImageDrawable(ResourcesCompat.getDrawable(res, plugin.getDarkIconId(), null));
                } else {
                    icon.setImageDrawable(ResourcesCompat.getDrawable(res, plugin.getLightIconId(), null));
                }
            } catch (PackageManager.NameNotFoundException e) {
                plugins[index] = null;
                label.setText("");
                icon.setImageDrawable(null);
            } catch (Resources.NotFoundException e) {
                icon.setImageResource(R.drawable.ic_favorite_m);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) switch (requestCode) {
            case REQUEST_PROFILE:
            {
                dismiss();
                Intent intent = new Intent(getActivity(), ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_TARGET, data.getLongExtra(AccountChooserActivity.EXTRA_SELECTED_USERID, -1));
                intent.putExtra(ProfileActivity.EXTRA_USER, data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD));
                startActivity(intent);
                break;
            }
            case REQUEST_TWILOG:
            {
                dismiss();
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(TwitterUtil.getTwilogURL(data.getStringExtra(AccountChooserActivity.EXTRA_SELECTED_USERSN))) ));
                break;
            }
            case REQUEST_ACCOUNT:
            {
                activeAccounts =
                        (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                createAccountIconView();
                //アクティブアカウントの再設定は別スレッドで行う
                ((Runnable) () -> ((TwitterServiceDelegate)getActivity()).getTwitterService().setActiveUsers(activeAccounts)).run();
                break;
            }
            default:
                if (REQUEST_PLUGIN_CHOOSE.contains(requestCode)) {
                    ResolveInfo info = data.getParcelableExtra(IntentChooserActivity.EXTRA_SELECT);
                    try {
                        MenuPlugin plugin = new MenuPlugin(getActivity(), info.activityInfo);
                        updatePlugin(REQUEST_PLUGIN_CHOOSE.indexOf(requestCode), plugin);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "プラグインの解決に失敗しました", Toast.LENGTH_SHORT).show();
                    }
                } else if (REQUEST_PLUGIN_EXEC.contains(requestCode)) {
                    dismiss();
                    AuthUserRecord userRecord = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    Intent intent = new Intent("jp.r246.twicca.ACTION_SHOW_USER");
                    intent.addCategory("jp.r246.twicca.category.OWNER");
                    intent.setComponent(plugins[REQUEST_PLUGIN_EXEC.indexOf(requestCode)].getComponentName());
                    intent.putExtra(Intent.EXTRA_TEXT, userRecord.ScreenName);
                    intent.putExtra("name", userRecord.Name);
                    intent.putExtra("id", String.valueOf(userRecord.NumericId));
                    intent.putExtra("profile_image_url", userRecord.ProfileImageUrl);
                    intent.putExtra("profile_image_url_mini", userRecord.ProfileImageUrl);
                    intent.putExtra("profile_image_url_normal", userRecord.ProfileImageUrl);
                    intent.putExtra("profile_image_url_bigger", userRecord.ProfileImageUrl);
                    intent.putExtra("owner_screen_name", userRecord.ScreenName);
                    intent.putExtra("owner_name", userRecord.Name);
                    intent.putExtra("owner_id", String.valueOf(userRecord.NumericId));
                    intent.putExtra("owner_profile_image_url", userRecord.ProfileImageUrl);
                    intent.putExtra("owner_profile_image_url_mini", userRecord.ProfileImageUrl);
                    intent.putExtra("owner_profile_image_url_normal", userRecord.ProfileImageUrl);
                    intent.putExtra("owner_profile_image_url_bigger", userRecord.ProfileImageUrl);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getActivity().getApplicationContext(), "プラグインの起動に失敗しました\nアプリが削除されましたか？", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    static class MenuPlugin {
        private String packageName;
        private String name;
        private String label;
        private String shortLabel;
        private int iconId;
        private int lightIconId;
        private int darkIconId;

        public MenuPlugin(Context context, ActivityInfo info) throws PackageManager.NameNotFoundException {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(info.applicationInfo.packageName, info.name);
            ActivityInfo metaInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA);

            packageName = info.applicationInfo.packageName;
            name = info.name;
            label = String.valueOf(info.loadLabel(packageManager));
            iconId = info.getIconResource();
            if (metaInfo.metaData != null) {
                shortLabel = metaInfo.metaData.getString("shibafu.yukari.PLUGIN_SHORT_LABEL");
                lightIconId = metaInfo.metaData.getInt("shibafu.yukari.PLUGIN_LIGHT_ICON");
                darkIconId = metaInfo.metaData.getInt("shibafu.yukari.PLUGIN_DARK_ICON");
            }
        }

        public String getPackageName() {
            return packageName;
        }

        public String getName() {
            return name;
        }

        public ComponentName getComponentName() {
            return new ComponentName(packageName, name);
        }

        public String getLabel() {
            return label;
        }

        public String getShortLabel() {
            return TextUtils.isEmpty(shortLabel) ? label : shortLabel;
        }

        public int getLightIconId() {
            return lightIconId == 0 ? iconId : lightIconId;
        }

        public int getDarkIconId() {
            return darkIconId == 0 ? iconId : darkIconId;
        }
    }
}
