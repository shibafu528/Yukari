package shibafu.yukari.fragment.base;

import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class YukariBaseFragment extends Fragment {
    @SuppressWarnings("deprecation")
    @Override
    public void onStart() {
        super.onStart();
        // onServiceConnected()がかつてサービスバインドで呼ばれていて、onStart()より若干遅れて実行されていたことの再現
        new Handler(Looper.getMainLooper()).post(this::onServiceConnected);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStop() {
        super.onStop();
        onServiceDisconnected();
    }

    @Deprecated
    public void onServiceConnected() {}

    @Deprecated
    public void onServiceDisconnected() {}
}
