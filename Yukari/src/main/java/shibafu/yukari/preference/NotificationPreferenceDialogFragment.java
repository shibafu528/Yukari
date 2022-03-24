package shibafu.yukari.preference;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import shibafu.yukari.R;
import shibafu.yukari.common.NotificationType;

public class NotificationPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
    private NotificationType value;

    public static NotificationPreferenceDialogFragment newInstance(String key) {
        NotificationPreferenceDialogFragment fragment = new NotificationPreferenceDialogFragment();
        Bundle b = new Bundle(1);
        b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        value = getNotificationPreference().getValue();

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_pref_notif, null);

        CheckBox cbEnabled = view.findViewById(R.id.cbEnableNotif);
        cbEnabled.setChecked(value.isEnabled());
        cbEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> value.setEnabled(isChecked));

        CheckBox cbUseSound = view.findViewById(R.id.cbEnableNotifSound);
        cbUseSound.setChecked(value.isUseSound());
        cbUseSound.setOnCheckedChangeListener((buttonView, isChecked) -> value.setUseSound(isChecked));

        CheckBox cbUseVibration = view.findViewById(R.id.cbEnableNotifVibration);
        cbUseVibration.setChecked(value.isUseVibration());
        cbUseVibration.setOnCheckedChangeListener((buttonView, isChecked) -> value.setUseVibration(isChecked));

        RadioGroup notificationTypeGroup = view.findViewById(R.id.rgNotifMode);
        if (value.getNotificationType() == NotificationType.TYPE_NOTIF) {
            ((RadioButton) notificationTypeGroup.findViewById(R.id.rbNotifStatus)).setChecked(true);
        } else {
            ((RadioButton) notificationTypeGroup.findViewById(R.id.rbNotifToast)).setChecked(true);
        }
        notificationTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbNotifStatus) {
                value.setNotificationType(NotificationType.TYPE_NOTIF);
            } else {
                value.setNotificationType(NotificationType.TYPE_TOAST);
            }
        });

        TextView tvOreoNotice = view.findViewById(R.id.tvNotifOreoNotice);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            tvOreoNotice.setVisibility(View.GONE);
        }

        builder.setView(view)
                .setOnDismissListener(NotificationPreferenceDialogFragment.this)
                .setNegativeButton(android.R.string.cancel, this)
                .setPositiveButton(android.R.string.ok, this);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            NotificationPreference preference = getNotificationPreference();
            if (preference.callChangeListener(value)) {
                preference.setValue(value);
            }
        }
    }

    private NotificationPreference getNotificationPreference() {
        return (NotificationPreference) getPreference();
    }
}
