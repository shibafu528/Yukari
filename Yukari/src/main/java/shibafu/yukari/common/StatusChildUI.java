package shibafu.yukari.common;

import android.app.Activity;
import android.support.annotation.Nullable;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Statusを操作するUIの構成要素のうち、子となるクラスが持つインターフェース
 */
public interface StatusChildUI {
    Activity getActivity();

    @Nullable
    default PreformedStatus getStatus() {
        if (getActivity() instanceof StatusUI) {
            return ((StatusUI) getActivity()).getStatus();
        }
        return null;
    }

    @Nullable
    default AuthUserRecord getUserRecord() {
        if (getActivity() instanceof StatusUI) {
            return ((StatusUI) getActivity()).getUserRecord();
        }
        return null;
    }

    default void setUserRecord(AuthUserRecord userRecord) {
        if (getActivity() instanceof StatusUI) {
            ((StatusUI) getActivity()).setUserRecord(userRecord);
        }
    }

    default void onUserChanged(AuthUserRecord userRecord) {}
}
