package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.AccountManageActivity;
import shibafu.yukari.activity.ConfigActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.IconLoaderTask;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;

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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(inflateView(getActivity().getLayoutInflater()));
        AlertDialog dialog = builder.create();
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (((MainActivity)getActivity()).isKeepScreenOn()) {
            keepScreenOnImage.setImageResource(R.drawable.ic_always_light_on);
        }
        activeAccounts = ((TwitterServiceDelegate)getActivity()).getTwitterService().getActiveUsers();
        createAccountIconView();
    }

    private void createAccountIconView() {
        llActiveAccounts.removeAllViewsInLayout();
        final int iconSize = (int) (getResources().getDisplayMetrics().density * ACCOUNT_ICON_DIP);
        for (AuthUserRecord user : activeAccounts) {
            ImageView iv = new ImageView(getActivity());
            iv.setFocusable(false);
            iv.setClickable(false);
            iv.setImageResource(R.drawable.yukatterload);
            iv.setTag(user.ProfileImageUrl);
            IconLoaderTask task = new IconLoaderTask(getActivity(), iv);
            task.executeIf(user.ProfileImageUrl);
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

        View aclogMenu = v.findViewById(R.id.llMenuAclog);
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
                MainActivity activity = ((MainActivity)getActivity());
                if (activity.isKeepScreenOn()) {
                    activity.setKeepScreenOn(false);
                    keepScreenOnImage.setImageResource(R.drawable.ic_always_light_off);
                }
                else {
                    activity.setKeepScreenOn(true);
                    keepScreenOnImage.setImageResource(R.drawable.ic_always_light_on);
                }
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
                ((TwitterServiceDelegate)getActivity()).getTwitterService().setActiveUsers(activeAccounts);
                break;
            }
        }
    }
}
