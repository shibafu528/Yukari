package shibafu.yukari.activity.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import shibafu.yukari.util.ThemeUtil;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ActionBarYukariBase extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (allowAutoTheme()) {
            ThemeUtil.setActivityTheme(this);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * @deprecated Override {@link #allowAutoTheme()} and return false.
     */
    @Deprecated
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

    @Deprecated
    public void onServiceConnected() {}

    protected boolean allowAutoTheme() {
        return true;
    }
}
