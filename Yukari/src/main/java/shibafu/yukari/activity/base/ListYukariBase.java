package shibafu.yukari.activity.base;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import shibafu.yukari.util.ThemeUtil;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ListYukariBase extends ListActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setActivityTheme(this);
        super.onCreate(savedInstanceState);
    }

    protected void onCreate(Bundle savedInstanceState, boolean ignoreAutoTheme) {
        super.onCreate(savedInstanceState);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onStart() {
        super.onStart();
        // onServiceConnected()がかつてサービスバインドで呼ばれていて、onStart()より若干遅れて実行されていたことの再現
        new Handler(Looper.getMainLooper()).post(this::onServiceConnected);
    }

    /**
     * @deprecated Implementations should move to {@link #onStart()}.
     */
    @Deprecated
    public void onServiceConnected() {}
}
