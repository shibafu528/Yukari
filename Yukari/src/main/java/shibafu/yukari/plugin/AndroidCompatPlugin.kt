package shibafu.yukari.plugin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import info.shibafu528.yukari.exvoice.Event
import info.shibafu528.yukari.exvoice.MRuby
import info.shibafu528.yukari.exvoice.Plugin
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.util.ObjectInspector

/**
 * Android互換レイヤープラグイン
 *
 * Created by shibafu on 2016/05/07.
 */
class AndroidCompatPlugin(mRuby: MRuby) : Plugin(mRuby, "android_compat") {

    @Event("intent")
    fun onIntent(options: Map<String, *>) {
        val extras = Bundle()
        options.forEach { entry ->
            val v = entry.value ?: return@forEach
            when (v) {
                is String -> extras.putString(entry.key, v.toString())
                is Long -> when (v) {
                    in Int.MIN_VALUE..Int.MAX_VALUE -> extras.putInt(entry.key, v.toInt())
                    else -> extras.putLong(entry.key, v)
                }
            }
        }

        val activity = options["activity"]?.toString() ?: ""
        val clazz = when (activity) {
            "TweetActivity" -> {
                when (options["mode"]) {
                    "reply" -> extras.putInt(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
                    "dm" -> extras.putInt(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM)
                    "quote" -> extras.putInt(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE)
                    else -> extras.putInt(TweetActivity.EXTRA_MODE, TweetActivity.MODE_TWEET)
                }
                TweetActivity::class.java
            }
            else -> {
                onMainThread {
                    Toast.makeText(context, "未実装ですヽ('ω')ﾉ三ヽ('ω')ﾉ\nもうしわけねぇもうしわけねぇ\n\n呼び出し情報 =>\n" + options.inspect(), Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        onMainThread {
            context.startActivity(Intent(context, clazz).putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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