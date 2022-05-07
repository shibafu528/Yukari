package shibafu.yukari.preference;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import android.util.AttributeSet;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import java.util.Locale;

import shibafu.yukari.common.NotificationType;

/**
 * Created by shibafu on 14/01/26.
 */
public class NotificationPreference extends DialogPreference {
    static {
        PreferenceFragmentCompat.registerPreferenceFragment(NotificationPreference.class, NotificationPreferenceDialogFragment.class);
    }

    private static final int defaultValue = 5;

    private NotificationType value = new NotificationType(defaultValue);

    public NotificationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public NotificationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationType getValue() {
        return value;
    }

    public void setValue(NotificationType preferenceValue) {
        setInternalValue(preferenceValue, false);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return new NotificationType(a.getInteger(index, NotificationPreference.defaultValue));
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        // 正直どれが返ってくるのかがリファレンスから理解できなかったので雑に網羅した
        int persisted;
        if (defaultValue == null) {
            persisted = getPersistedInt(NotificationPreference.defaultValue);
        } else if (defaultValue instanceof Integer) {
            persisted = getPersistedInt((Integer) defaultValue);
        } else if (defaultValue instanceof NotificationType) {
            persisted = ((NotificationType) defaultValue).toInteger();
        } else {
            throw new RuntimeException(String.format(Locale.US, "Unexpected default value: %s (%s)", defaultValue, defaultValue.getClass().getName()));
        }
        setInternalValue(new NotificationType(persisted), true);
    }

    private void setInternalValue(NotificationType value, boolean force) {
        int oldValue = getPersistedInt(defaultValue);
        boolean changed = value.toInteger() != oldValue;
        if (changed || force) {
            this.value = value;

            persistInt(value.toInteger());

            notifyChanged();
        }
    }

}
