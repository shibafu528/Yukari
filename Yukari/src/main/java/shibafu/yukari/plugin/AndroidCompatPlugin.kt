package shibafu.yukari.plugin

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import info.shibafu528.yukari.exvoice.Event
import info.shibafu528.yukari.exvoice.MRuby
import info.shibafu528.yukari.exvoice.Plugin
import shibafu.yukari.util.ObjectInspector

/**
 * Android互換レイヤープラグイン
 *
 * Created by shibafu on 2016/05/07.
 */
class AndroidCompatPlugin(mRuby: MRuby) : Plugin(mRuby, "android_compat") {

    @Event("intent")
    fun onIntent(options: Map<String, *>) {
        onMainThread {
            Toast.makeText(context, "未実装ですヽ('ω')ﾉ三ヽ('ω')ﾉ\nもうしわけねぇもうしわけねぇ\n\n呼び出し情報 =>\n" + options.inspect(), Toast.LENGTH_SHORT).show()
        }
    }

    @Event("toast")
    fun onToast(text: String) {
        onMainThread {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    inline fun onMainThread(crossinline action: () -> Unit) {
        Handler(Looper.getMainLooper()).post { action() }
    }

    fun Any.inspect(): String = ObjectInspector.inspect(this)
}