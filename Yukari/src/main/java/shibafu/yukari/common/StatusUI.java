package shibafu.yukari.common;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Statusを操作するUIの構成要素のうち、親となるクラスが持つインターフェース
 */
public interface StatusUI {
    PreformedStatus getStatus();

    AuthUserRecord getUserRecord();
    void setUserRecord(AuthUserRecord userRecord);
}
