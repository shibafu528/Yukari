package shibafu.yukari.plugin;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import info.shibafu528.yukari.exvoice.Event;
import info.shibafu528.yukari.exvoice.MRuby;
import info.shibafu528.yukari.exvoice.Plugin;
import shibafu.yukari.activity.TweetActivity;

/**
 * Javaプラグインの実装サンプル
 *
 * Created by shibafu on 2016/05/02.
 */
public class SamplePlugin extends Plugin {

    public SamplePlugin(MRuby mRuby) {
        super(mRuby, "java_sample");
    }

    @Event("java_sample")
    public void onJavaSample(String arg1) {
        Log.d("SamplePlugin", "pyaaaaaaaaaaaaaa : " + arg1);

        new Handler(Looper.getMainLooper()).post(() -> {
            getContext().startActivity(
                    new Intent(getContext(), TweetActivity.class)
                            .putExtra(TweetActivity.EXTRA_TEXT, "ﾋﾟｬｱｱｱｱｱｱｱｱｱｱｱwwwwwwwwwwwwwwwwww")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    }
}
