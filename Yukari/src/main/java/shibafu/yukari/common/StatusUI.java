package shibafu.yukari.common;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import shibafu.yukari.entity.Status;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Statusを操作するUIの構成要素のうち、親となるクラスが持つインターフェース
 */
public interface StatusUI {
    @NonNull
    Status getStatus();

    @Nullable
    AuthUserRecord getUserRecord();
    void setUserRecord(AuthUserRecord userRecord);
}
