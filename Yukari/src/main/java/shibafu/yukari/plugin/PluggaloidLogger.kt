package shibafu.yukari.plugin

import java.text.SimpleDateFormat
import java.util.*

class PluggaloidLogger {
    private val buffer = StringBuffer()
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)

    fun log(s: CharSequence) {
        buffer.append("[").append(dateFormat.format(Date())).append("] ").append(s).append("\n")
    }

    override fun toString(): String = buffer.toString()
}