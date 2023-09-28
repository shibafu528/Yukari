package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.ChannelManageActivity;
import shibafu.yukari.activity.ConfigActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.databinding.DialogMenuBinding;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.linkage.StreamChannel;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.util.AttrUtil;

/**
 * Created by Shibafu on 13/12/16.
 */
public class MenuDialogFragment extends DialogFragment {

    private static final int REQUEST_PROFILE = 1;

    private static final int ACCOUNT_ICON_DIP = 32;

    private DialogMenuBinding binding;

    private ArrayList<AuthUserRecord> activeAccounts;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light").endsWith("dark")) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.menu_dialog_full_material_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.menu_dialog_full_material_light);
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
            binding.ivMenuSleepIcon.setImageResource(R.drawable.ic_always_light_on);
        }

        class AccountsLoader extends SimpleAsyncTask {
            @Override
            protected Void doInBackground(Void... params) {
                MainActivity activity = (MainActivity) getActivity();
                if (activity == null) {
                    return null;
                }
                while (!activity.isTwitterServiceBound()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }

                final ProviderStream[] streams = activity.getTwitterService().getProviderStreams();
                ArrayList<AuthUserRecord> actives = new ArrayList<>();
                for (ProviderStream stream : streams) {
                    if (stream != null) {
                        for (StreamChannel channel : stream.getChannels()) {
                            final AuthUserRecord userRecord = channel.getUserRecord();
                            if (channel.isRunning() && !actives.contains(userRecord)) {
                                actives.add(userRecord);
                            }
                        }
                    }
                }
                activeAccounts = actives;

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (activeAccounts != null && isResumed()) {
                    createAccountIconView();
                }
            }
        }
        new AccountsLoader().executeParallel();
    }

    private void createAccountIconView() {
        binding.llMenuAccounts.removeAllViewsInLayout();
        final int iconSize = (int) (getResources().getDisplayMetrics().density * ACCOUNT_ICON_DIP);
        for (AuthUserRecord user : activeAccounts) {
            ImageView iv = new ImageView(getActivity());
            iv.setFocusable(false);
            iv.setClickable(false);
            ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), iv, user.ProfileImageUrl);
            binding.llMenuAccounts.addView(iv, iconSize, iconSize);
        }
    }

    private View inflateView(LayoutInflater inflater) {
        binding = DialogMenuBinding.inflate(inflater);

        binding.llMenuAccountParent.setOnClickListener(v1 -> {
            dismiss();
            Intent intent = new Intent(getActivity(), ChannelManageActivity.class);
            startActivity(intent);
        });

        binding.llMenuProfile.setOnClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            startActivityForResult(intent, REQUEST_PROFILE);
        });

        binding.llMenuExit.setOnClickListener(v1 -> {
            dismiss();
            ((MainActivity) getActivity()).showExitDialog();
        });

        binding.llMenuSleep.setOnClickListener(v1 -> {
            MainActivity activity = ((MainActivity) getActivity());
            if (activity.isKeepScreenOn()) {
                activity.setKeepScreenOn(false);
                binding.ivMenuSleepIcon.setImageResource(AttrUtil.resolveAttribute(getDialog().getContext().getTheme(), R.attr.menuBacklightDrawable));
            } else {
                activity.setKeepScreenOn(true);
                binding.ivMenuSleepIcon.setImageResource(R.drawable.ic_always_light_on);
            }
        });
        binding.llMenuSleep.setOnLongClickListener(v1 -> {
            MainActivity activity = ((MainActivity) getActivity());
            activity.setImmersive(!activity.isImmersive());
            Toast.makeText(getActivity(), "表示域拡張を" + ((activity.isImmersive()) ? "有効" : "無効") + "にしました", Toast.LENGTH_SHORT).show();
            return true;
        });

        binding.llMenuBookmark.setOnClickListener(view -> {
            dismiss();
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_BOOKMARK);
            startActivity(intent);
        });

        binding.llMenuConfig.setOnClickListener(v1 -> {
            dismiss();
            startActivity(new Intent(getActivity(), ConfigActivity.class));
        });

        binding.ibMenuReconnect.setOnClickListener(v1 -> {
            Toast.makeText(getActivity(), "再接続します...", Toast.LENGTH_LONG).show();
            dismiss();
            AsyncReconnectTask task = new AsyncReconnectTask();
            task.executeParallel(((TwitterServiceDelegate) getActivity()).getTwitterService());
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) switch (requestCode) {
            case REQUEST_PROFILE: {
                dismiss();
                AuthUserRecord userRecord = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                Intent intent = ProfileActivity.newIntent(getActivity(),
                        userRecord,
                        Uri.parse(userRecord.Url));
                startActivity(intent);
                break;
            }
        }
    }

    private static class AsyncReconnectTask extends ParallelAsyncTask<TwitterService, Void, Void> {
        @Override
        protected Void doInBackground(TwitterService... twitterServices) {
            twitterServices[0].reconnectStreamChannels();
            return null;
        }
    }
}
