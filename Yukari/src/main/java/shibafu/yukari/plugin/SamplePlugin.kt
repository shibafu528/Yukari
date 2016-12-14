package shibafu.yukari.plugin

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import info.shibafu528.yukari.exvoice.Event
import info.shibafu528.yukari.exvoice.Filter
import info.shibafu528.yukari.exvoice.MRuby
import info.shibafu528.yukari.exvoice.Plugin
import shibafu.yukari.activity.TweetActivity

/**
 * Javaプラグインの実装サンプル
 *
 * Created by shibafu on 2016/05/02.
 */
class SamplePlugin(mRuby: MRuby) : Plugin(mRuby, "java_sample") {

    @Event("java_sample")
    fun onJavaSample(arg1: String) {
        Log.d("SamplePlugin", "pyaaaaaaaaaaaaaa : " + arg1)

        Handler(Looper.getMainLooper()).post {
            context.startActivity(
                    Intent(context, TweetActivity::class.java)
                            .putExtra(TweetActivity.EXTRA_TEXT, "ﾋﾟｬｱｱｱｱｱｱｱｱｱｱｱwwwwwwwwwwwwwwwwww")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Filter("java_sample")
    fun filterJavaSample(arg1: String): Array<Any> = arrayOf("pyaaaaaaaaaaaaaaaaaaaaaaaaa " + arg1)
}
