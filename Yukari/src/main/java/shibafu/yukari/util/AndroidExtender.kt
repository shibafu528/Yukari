package shibafu.yukari.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * Created by shibafu on 2015/07/27.
 */

public fun Context.getDefaultSharedPreferences(): SharedPreferences
        = PreferenceManager.getDefaultSharedPreferences(this)