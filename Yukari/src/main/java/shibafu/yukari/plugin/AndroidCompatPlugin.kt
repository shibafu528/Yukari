package shibafu.yukari.plugin

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import info.shibafu528.yukari.exvoice.Event
import info.shibafu528.yukari.exvoice.MRuby
import info.shibafu528.yukari.exvoice.Plugin

/**
 * Android互換レイヤープラグイン
 *
 * Created by shibafu on 2016/05/07.
 */
class AndroidCompatPlugin(mRuby: MRuby) : Plugin(mRuby, "android_compat") {

    @Event("toast")
    fun onToast(text: String) {
        onMainThread {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    inline fun onMainThread(crossinline action: () -> Unit) {
        Handler(Looper.getMainLooper()).post { action() }
    }
}