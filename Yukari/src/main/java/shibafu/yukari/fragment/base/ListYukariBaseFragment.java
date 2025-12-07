package shibafu.yukari.fragment.base;

import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.ListFragment;

/**
 * Created by shibafu on 14/07/12.
 */
public abstract class ListYukariBaseFragment extends ListFragment {
    @SuppressWarnings("deprecation")
    @Override
    public void onStart() {
        super.onStart();
        // onServiceConnected()がかつてサービスバインドで呼ばれていて、onStart()より若干遅れて実行されていたことの再現
        new Handler(Looper.getMainLooper()).post(this::onServiceConnected);
    }

    @Deprecated
    public void onServiceConnected() {}
}
