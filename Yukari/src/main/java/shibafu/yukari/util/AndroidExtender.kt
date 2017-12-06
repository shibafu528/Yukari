package shibafu.yukari.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v4.util.SparseArrayCompat
import android.util.Log
import android.widget.Toast

/**
 * Created by shibafu on 2015/07/27.
 */

public val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)

public val Fragment.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this.activity.applicationContext)

public fun Context.showToast(text: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this.applicationContext, text, duration).show()
}

public fun Fragment.showToast(text: String, duration: Int = Toast.LENGTH_SHORT) {
    this.activity.applicationContext.showToast(text, duration)
}

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
public fun Context.putErrorLog(s: String, vararg param: Any) {
    Log.e(LOG_TAG, java.lang.String.format(s, *param))
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
public fun Context.putDebugLog(s: String, vararg param: Any) {
    Log.d(LOG_TAG, java.lang.String.format(s, *param))
}

/**
 * [Class.getSimpleName] を返します。
 */
public val Fragment.LOG_TAG: String
    get() = javaClass.simpleName

/**
 * Log.e(ClassName, s)
 */
public fun Fragment.putErrorLog(s: String) {
    Log.e(LOG_TAG, s)
}

/**
 * Log.e(ClassName, String.format(s, param))
 */
public fun Fragment.putErrorLog(s: String, vararg param: Any) {
    Log.e(LOG_TAG, java.lang.String.format(s, *param))
}

/**
 * Log.d(ClassName, s)
 */
public fun Fragment.putDebugLog(s: String) {
    Log.d(LOG_TAG, s)
}

/**
 * Log.d(ClassName, String.format(s, param))
 */
public fun Fragment.putDebugLog(s: String, vararg param: Any) {
    Log.d(LOG_TAG, java.lang.String.format(s, *param))
}

fun Any.putDebugLog(s: String) {
    Log.d(javaClass.simpleName, s)
}

public fun <T> SparseArrayCompat<T>.forEach(operation: (Int, T) -> Unit) {
    for (i in 0..size()-1) {
        operation(keyAt(i), valueAt(i))
    }
}

public fun <T, R> SparseArrayCompat<T>.map(operation: (Int, T) -> R): List<R> {
    var mappedValues = emptyList<R>()
    for (i in 0..size()-1) {
        mappedValues += operation(keyAt(i), valueAt(i))
    }
    return mappedValues
}

public operator fun <T> SparseArrayCompat<T>.set(key: Int, value: T) {
    this.put(key, value)
}