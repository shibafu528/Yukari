package shibafu.yukari.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import shibafu.yukari.af2015.R;

/**
 * Created by shibafu on 14/01/26.
 */
public class NotificationPreference extends DialogPreference{

    private NotificationType preferenceValue = new NotificationType(5);

    private CheckBox cbEnabled, cbUseSound, cbUseVibration;
    private RadioGroup notificationTypeGroup;

    public NotificationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public NotificationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return new NotificationType(a.getInteger(index, 5));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            preferenceValue = new NotificationType(getPersistedInt(preferenceValue.toInteger()));
        }
        else {
            preferenceValue = (NotificationType)defaultValue;
            persistInt(preferenceValue.toInteger());
        }
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_pref_notif, null);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB &&
                PreferenceManager.getDefaultSharedPreferences(getContext()).getString("pref_theme", "light").equals("light")) {
            view.setBackgroundColor(Color.WHITE);
        }
        cbEnabled = (CheckBox) view.findViewById(R.id.cbEnableNotif);
        cbEnabled.setChecked(preferenceValue.isEnabled());
        cbUseSound = (CheckBox) view.findViewById(R.id.cbEnableNotifSound);
        cbUseSound.setChecked(preferenceValue.isUseSound());
        cbUseVibration = (CheckBox) view.findViewById(R.id.cbEnableNotifVibration);
        cbUseVibration.setChecked(preferenceValue.isUseVibration());
        notificationTypeGroup = (RadioGroup) view.findViewById(R.id.rgNotifMode);
        if (preferenceValue.getNotificationType() == NotificationType.TYPE_NOTIF) {
            ((RadioButton)notificationTypeGroup.findViewById(R.id.rbNotifStatus)).setChecked(true);
        }
        else {
            ((RadioButton)notificationTypeGroup.findViewById(R.id.rbNotifToast)).setChecked(true);
        }
        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            preferenceValue.setEnabled(cbEnabled.isChecked());
            if (notificationTypeGroup.getCheckedRadioButtonId() == R.id.rbNotifStatus) {
                preferenceValue.setNotificationType(NotificationType.TYPE_NOTIF);
            }
            else {
                preferenceValue.setNotificationType(NotificationType.TYPE_TOAST);
            }
            preferenceValue.setUseSound(cbUseSound.isChecked());
            preferenceValue.setUseVibration(cbUseVibration.isChecked());

            int newValue = preferenceValue.toInteger();
            if (callChangeListener(newValue)) {
                persistInt(newValue);
            }
        }
        super.onDialogClosed(positiveResult);
    }
}
