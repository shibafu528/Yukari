package shibafu.yukari.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log

/**
 * Created by shibafu on 2015/07/27.
 */

public fun Context.getDefaultSharedPreferences(): SharedPreferences
        = PreferenceManager.getDefaultSharedPreferences(this)

/**
 * [Class.getSimpleName] を返します。
 */
public val Context.LOG_TAG: String
    get() = javaClass.simpleName

/**
 * Log.e(ClassName, s)
 */
public fun Context.putErrorLog(s: String) {
    Log.e(LOG_TAG, s)
}

/**
 * Log.e(ClassName, String.format(s, param))
 */
public fun Context.putErrorLog(s: String, vararg param: String) {
    Log.e(LOG_TAG, java.lang.String.format(s, param))
}

/**
 * Log.d(ClassName, s)
 */
public fun Context.putDebugLog(s: String) {
    Log.d(LOG_TAG, s)
}

/**
 * Log.d(ClassName, String.format(s, param))
 */
public fun Context.putDebugLog(s: String, vararg param: String) {
    Log.d(LOG_TAG, java.lang.String.format(s, param))
}