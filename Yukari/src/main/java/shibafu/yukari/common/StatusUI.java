package shibafu.yukari.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import shibafu.yukari.entity.Status;
import shibafu.yukari.database.AuthUserRecord;

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
