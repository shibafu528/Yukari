package shibafu.yukari.common;

import shibafu.yukari.database.AuthUserRecord;

/**
 * Statusを操作するUIの構成要素のうち、子となるクラスが持つインターフェース
 */
public interface StatusChildUI {
    void onUserChanged(AuthUserRecord userRecord);
}
