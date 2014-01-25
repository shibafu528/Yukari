package shibafu.yukari.common;

/**
 * Created by shibafu on 14/01/26.
 */
public class NotificationType {
    //有効か
    public static final int ENABLED = 0x01;

    //通知タイプ
    public static final int TYPE_NOTIF = 0x00;
    public static final int TYPE_TOAST = 0x02;

    //通知デバイス
    public static final int DEVICE_SOUND = 0x04;
    public static final int DEVICE_VIB   = 0x08;

    private int value;

    public NotificationType(int value) {
        this.value = value;
    }

    public void setEnabled(boolean enabled) {
        value = (value & (0xff - ENABLED)) | (enabled? ENABLED : 0);
    }

    public boolean isEnabled() {
        return (value & ENABLED) == ENABLED;
    }

    public void setNotificationType(int type) {
        value = (value & (0xff - TYPE_TOAST)) | type;
    }

    public int getNotificationType() {
        return (value & TYPE_TOAST);
    }

    public void setUseSound(boolean enabled) {
        value = (value & (0xff - DEVICE_SOUND)) | (enabled? DEVICE_SOUND : 0);
    }

    public boolean isUseSound() {
        return (value & DEVICE_SOUND) == DEVICE_SOUND;
    }

    public void setUseVibration(boolean enabled) {
        value = (value & (0xff - DEVICE_VIB)) | (enabled? DEVICE_VIB : 0);
    }

    public boolean isUseVibration() {
        return (value & DEVICE_VIB) == DEVICE_VIB;
    }

    public int toInteger() {
        return value;
    }
}
