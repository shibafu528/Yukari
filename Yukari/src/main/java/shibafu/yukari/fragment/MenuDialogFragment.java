package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.ConfigActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.util.AttrUtil;

/**
 * Created by Shibafu on 13/12/16.
 */
public class MenuDialogFragment extends DialogFragment {

    private static final int REQUEST_PROFILE = 1;
    private static final int REQUEST_TWILOG = 2;
    private static final int REQUEST_FAVSTAR = 3;
    private static final int REQUEST_ACLOG = 4;
    private static final int REQUEST_ACCOUNT = 5;

    private static final int ACCOUNT_ICON_DIP = 32;

    private LinearLayout llActiveAccounts;
    private ArrayList<AuthUserRecord> activeAccounts;

    private ImageView keepScreenOnImage;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);
                break;
            case "dark":
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_dark);
                break;
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
        v.findViewById(R.id.llMenuAccountParent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
                intent.putExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS, activeAccounts);
                startActivityForResult(intent, REQUEST_ACCOUNT);
            }
        });

        View profileMenu = v.findViewById(R.id.llMenuProfile);
        profileMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_PROFILE);
            }
        });

        View twilogMenu = v.findViewById(R.id.llMenuTwilog);
        twilogMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_TWILOG);
            }
        });

        View favstarMenu = v.findViewById(R.id.llMenuFavstar);
        favstarMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_FAVSTAR);
            }
        });

        final View aclogMenu = v.findViewById(R.id.llMenuAclog);
        aclogMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                startActivityForResult(intent, REQUEST_ACLOG);
            }
        });

        View exitMenu = v.findViewById(R.id.llMenuExit);
        exitMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                ((MainActivity)getActivity()).showExitDialog();
            }
        });

        keepScreenOnImage = (ImageView) v.findViewById(R.id.ivMenuSleepIcon);

        View keepScreenOnMenu = v.findViewById(R.id.llMenuSleep);
        keepScreenOnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity activity = ((MainActivity) getActivity());
                if (activity.isKeepScreenOn()) {
                    activity.setKeepScreenOn(false);
                    keepScreenOnImage.setImageResource(AttrUtil.resolveAttribute(getDialog().getContext().getTheme(), R.attr.menuBacklightDrawable));
                } else {
                    activity.setKeepScreenOn(true);
                    keepScreenOnImage.setImageResource(R.drawable.ic_always_light_on);
                }
            }
        });
        keepScreenOnMenu.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    MainActivity activity = ((MainActivity) getActivity());
                    activity.setImmersive(!activity.isImmersive());
                    Toast.makeText(getActivity(), "表示域拡張を" + ((activity.isImmersive()) ? "有効" : "無効") + "にしました", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });

        View bookmarkMenu = v.findViewById(R.id.llMenuBookmark);
        bookmarkMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_BOOKMARK);
                startActivity(intent);
            }
        });

        View configMenu = v.findViewById(R.id.llMenuConfig);
        configMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                startActivity(new Intent(getActivity(), ConfigActivity.class));
            }
        });

        ImageButton ibReconnect = (ImageButton) v.findViewById(R.id.ibMenuReconnect);
        ibReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "再接続します...", Toast.LENGTH_LONG).show();
                dismiss();
                ((TwitterServiceDelegate)getActivity()).getTwitterService().getStatusManager().reconnectAsync();
            }
        });

        return v;
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
            case REQUEST_FAVSTAR:
            {
                dismiss();
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(TwitterUtil.getFavstarURL(data.getStringExtra(AccountChooserActivity.EXTRA_SELECTED_USERSN))) ));
                break;
            }
            case REQUEST_ACLOG:
            {
                dismiss();
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(TwitterUtil.getAclogURL(data.getStringExtra(AccountChooserActivity.EXTRA_SELECTED_USERSN))) ));
                break;
            }
            case REQUEST_ACCOUNT:
            {
                activeAccounts =
                        (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                createAccountIconView();
                //アクティブアカウントの再設定は別スレッドで行う
                new Runnable() {
                    @Override
                    public void run() {
                        ((TwitterServiceDelegate)getActivity()).getTwitterService().setActiveUsers(activeAccounts);
                    }
                }.run();
                break;
            }
        }
    }
}
