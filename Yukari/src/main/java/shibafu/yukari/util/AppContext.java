package shibafu.yukari.util;

import android.content.Context;
import shibafu.yukari.core.YukariApplication;

/**
 * Created by shibafu on 2016/04/23.
 */
public interface AppContext {
    Context getApplicationContext();

    default YukariApplication getYukariContext() {
        return (YukariApplication) getApplicationContext();
    }
}
