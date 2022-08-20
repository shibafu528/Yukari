package shibafu.yukari.common

import android.net.Uri
import android.provider.Settings

object NotificationPreferenceSoundUri {
    @JvmStatic
    fun parse(prefValue: String?): Uri? = when (prefValue) {
        null -> Settings.System.DEFAULT_NOTIFICATION_URI
        "null" -> null
        else -> Uri.parse(prefValue)
    }

    @JvmStatic
    fun toString(uri: Uri?): String = uri.toString() // Any.toString() = if (this == null) { "null" } else { this.toString() }
}