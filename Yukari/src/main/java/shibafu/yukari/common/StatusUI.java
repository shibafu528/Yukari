package shibafu.yukari.common;

import shibafu.yukari.entity.Status;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Statusを操作するUIの構成要素のうち、親となるクラスが持つインターフェース
 */
public interface StatusUI {
    Status getStatus();

    AuthUserRecord getUserRecord();
    void setUserRecord(AuthUserRecord userRecord);
}
