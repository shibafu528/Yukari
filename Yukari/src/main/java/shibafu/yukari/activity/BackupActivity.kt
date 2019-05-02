package shibafu.yukari.activity

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v4.app.DialogFragment
import android.support.v4.app.ListFragment
import android.support.v4.util.SparseArrayCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Spinner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.PermissionUtils
import permissions.dispatcher.RuntimePermissions
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.NotificationChannelPrefix
import shibafu.yukari.common.TabInfo
import shibafu.yukari.common.UsedHashes
import shibafu.yukari.common.async.SimpleAsyncTask
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.DBRecord
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.Provider
import shibafu.yukari.database.SearchHistory
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.database.UserExtras
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.export.ConfigFileUtility
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.forEach
import shibafu.yukari.util.set
import shibafu.yukari.util.showToast
import twitter4j.auth.AccessToken
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Created by shibafu on 2016/03/13.
 */
@RuntimePermissions
class BackupActivity : ActionBarYukariBase(), SimpleAlertDialogFragment.OnDialogChoseListener {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODE_IMPORT = 0
        const val EXTRA_MODE_EXPORT = 1

        private const val FRAGMENT_TAG = "backup"

        private const val DIALOG_IMPORT_FINISHED = 1
        private const val DIALOG_EXPORT_FINISHED = 2
        private const val DIALOG_PERMISSION_DENIED = 3
    }

    val spLocation: Spinner by lazy { findViewById<Spinner>(R.id.spinner) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        if (!intent.hasExtra(EXTRA_MODE) || intent.getIntExtra(EXTRA_MODE, -1) !in arrayOf(EXTRA_MODE_IMPORT, EXTRA_MODE_EXPORT)) {
            showToast("Missing mode")
            finish()
            return
        }

        val mode = intent.getIntExtra(EXTRA_MODE, EXTRA_MODE_EXPORT)
        when (mode) {
            EXTRA_MODE_IMPORT -> setTitle(R.string.title_activity_backup_import)
            EXTRA_MODE_EXPORT -> setTitle(R.string.title_activity_backup_export)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame, BackupFragment(), FRAGMENT_TAG)
                .commit()
        }

        findViewById<Button>(R.id.btnExecute).setOnClickListener { onClickExecuteWithPermissionCheck() }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onClickExecute() {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment !is BackupFragment) throw RuntimeException("fragment type mismatched")

        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED || Environment.getExternalStorageDirectory() == null) {
            showToast("SDカードが挿入されていないか、ストレージ領域を正しく認識できていません。")
            return
        }

        val database = twitterService.database
        when (intent.getIntExtra(EXTRA_MODE, EXTRA_MODE_EXPORT)) {
            EXTRA_MODE_IMPORT -> {
                object : SimpleAsyncTask() {
                    private var progressFragment: DialogFragment? = null

                    override fun doInBackground(vararg p0: Void?): Void? {
                        database.beginTransaction()
                        try {
                            val readFile: (String) -> String = when (spLocation.selectedItemPosition) {
                                0 -> lambda@ { fileName ->
                                    // import from file
                                    val directory = File(Environment.getExternalStorageDirectory(), "Yukari4a")
                                    if (!directory.exists()) {
                                        directory.mkdirs()
                                    }

                                    val file = File(directory, fileName)
                                    if (!file.exists()) {
                                        throw FileNotFoundException("not found : $fileName")
                                    }
                                    FileInputStream(file).use {
                                        it.reader().use {
                                            return@lambda it.readText()
                                        }
                                    }
                                }
                                1 -> lambda@ { fileName ->
                                    // import from Google Drive
                                    return@lambda ""
                                }
                                else -> throw RuntimeException("invalid location choose")
                            }

                            fun <F, T : DBRecord> importIntoDatabase(from: Class<F>, to: Class<T>, json: String)
                                    = database.importRecordMaps(to, ConfigFileUtility.importFromJson(from, json))
                            fun <F> importIntoDatabase(from: Class<F>, to: String, json: String)
                                    = database.importRecordMaps(to, ConfigFileUtility.importFromJson(from, json))

                            fragment.checkedStates.forEach { i, b ->
                                try {
                                    when (i) {
                                        1 -> {
                                            val records = ConfigFileUtility.importFromJson(AuthUserRecord::class.java, readFile("accounts.json"))
                                            database.importRecordMaps(AuthUserRecord::class.java, records)

                                            // アカウントレベルの通知チャンネルを全て削除し、次回起動時に再生成させる
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                                nm.notificationChannels.forEach { channel ->
                                                    val groupId = channel.group
                                                    if (groupId != null && groupId.startsWith(NotificationChannelPrefix.GROUP_ACCOUNT)) {
                                                        nm.deleteNotificationChannel(channel.id)
                                                    }
                                                }
                                                nm.notificationChannelGroups.forEach { group ->
                                                    if (group.id.startsWith(NotificationChannelPrefix.GROUP_ACCOUNT)) {
                                                        nm.deleteNotificationChannelGroup(group.id)
                                                    }
                                                }
                                            }

                                            // y4a 2.0以下との互換処理。Twitterアカウントのプロフィール情報を取得する。
                                            val twitter = twitterService.getTwitterOrThrow(null)
                                            records.forEach eachRecord@{
                                                // ProviderID == null -> Twitter
                                                if (it.containsKey(CentralDatabase.COL_ACCOUNTS_PROVIDER_ID)) {
                                                    return@eachRecord
                                                }

                                                twitter.oAuthAccessToken = AccessToken(
                                                        it[CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN].toString(),
                                                        it[CentralDatabase.COL_ACCOUNTS_ACCESS_TOKEN_SECRET].toString()
                                                )

                                                val user = twitter.showUser(twitter.oAuthAccessToken.userId)
                                                database.updateAccountProfile(Provider.TWITTER.id, user.id, user.screenName, user.name, user.originalProfileImageURLHttps)
                                            }
                                        }
                                        2 -> importIntoDatabase(TabInfo::class.java, TabInfo::class.java, readFile("tabs.json"))
                                        3 -> importIntoDatabase(MuteConfig::class.java, MuteConfig::class.java, readFile("mute.json"))
                                        4 -> importIntoDatabase(AutoMuteConfig::class.java, AutoMuteConfig::class.java, readFile("automute.json"))
                                        5 -> importIntoDatabase(UserExtras::class.java, UserExtras::class.java, readFile("user_extras.json"))
                                        6 -> importIntoDatabase(Bookmark.SerializeEntity::class.java, Bookmark::class.java, readFile("bookmarks.json"))
                                        7 -> importIntoDatabase(StatusDraft::class.java, CentralDatabase.TABLE_DRAFTS, readFile("drafts.json"))
                                        8 -> importIntoDatabase(SearchHistory::class.java, SearchHistory::class.java, readFile("draftsearch_history.json"))
                                        9 -> {
                                            val records: List<String> = Gson().fromJson(readFile("used_tags.json"), object : TypeToken<List<String>>() {}.type)
                                            val usedHashes = UsedHashes(applicationContext)
                                            records.forEach { usedHashes.put(it) }
                                            usedHashes.save(applicationContext)
                                        }
                                        10 -> importIntoDatabase(StreamChannelState::class.java, StreamChannelState::class.java, readFile("stream_channel_states.json"))
                                        11 -> {
                                            val records: Map<String, Any?> = Gson().fromJson(readFile("prefs.json"), object : TypeToken<Map<String, Any?>>() {}.type)
                                            val spEdit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                                            records.forEach {
                                                var value = it.value
                                                if (value is Double) {
                                                    if (value - Math.floor(value) == 0.0) {
                                                        value = value.toInt()
                                                    } else {
                                                        value = value.toFloat()
                                                    }
                                                }
                                                when (value) {
                                                    is String -> spEdit.putString(it.key, value)
                                                    is Long -> spEdit.putLong(it.key, value)
                                                    is Int -> spEdit.putInt(it.key, value)
                                                    is Float -> spEdit.putFloat(it.key, value)
                                                    is Boolean -> spEdit.putBoolean(it.key, value)
                                                }
                                            }
                                            spEdit.apply()
                                        }
                                    }
                                } catch (e: FileNotFoundException) {}
                            }

                            database.setTransactionSuccessful()
                        } finally {
                            database.endTransaction()
                        }
                        return null
                    }

                    override fun onPreExecute() {
                        super.onPreExecute()
                        progressFragment = MaintenanceActivity.SimpleProgressDialogFragment.newInstance(null, "インポート中...", true, false)
                        progressFragment?.show(supportFragmentManager, "progress")
                    }

                    override fun onPostExecute(result: Void?) {
                        super.onPostExecute(result)
                        progressFragment?.dismiss()
                        progressFragment = null
                        SimpleAlertDialogFragment.newInstance(DIALOG_IMPORT_FINISHED,
                                "インポート完了", "設定を反映させるため、アプリを再起動します。\nOKをタップした後、そのまましばらくお待ち下さい。",
                                "OK", null)
                                .show(supportFragmentManager, "import_finished")
                    }
                }.execute()
            }
            EXTRA_MODE_EXPORT -> {
                object : SimpleAsyncTask() {
                    private var progressFragment: DialogFragment? = null

                    override fun doInBackground(vararg p0: Void?): Void? {
                        val exports = mutableMapOf<String, String>()
                        fragment.checkedStates.forEach { i, b ->
                            @Suppress("UNCHECKED_CAST")
                            when (i) {
                                1 -> exports["accounts.json"] = ConfigFileUtility.exportToJson(AuthUserRecord::class.java, database.getRecordMaps(AuthUserRecord::class.java) as List<Map<String, Any?>>)
                                2 -> exports["tabs.json"] = ConfigFileUtility.exportToJson(TabInfo::class.java, database.getRecordMaps(TabInfo::class.java) as List<Map<String, Any?>>)
                                3 -> exports["mute.json"] = ConfigFileUtility.exportToJson(MuteConfig::class.java, database.getRecordMaps(MuteConfig::class.java) as List<Map<String, Any?>>)
                                4 -> exports["automute.json"] = ConfigFileUtility.exportToJson(AutoMuteConfig::class.java, database.getRecordMaps(AutoMuteConfig::class.java) as List<Map<String, Any?>>)
                                5 -> exports["user_extras.json"] = ConfigFileUtility.exportToJson(UserExtras::class.java, database.getRecordMaps(UserExtras::class.java) as List<Map<String, Any?>>)
                                6 -> exports["bookmarks.json"] = ConfigFileUtility.exportToJson(Bookmark.SerializeEntity::class.java, database.getRecordMaps(Bookmark::class.java) as List<Map<String, Any?>>)
                                7 -> exports["drafts.json"] = ConfigFileUtility.exportToJson(StatusDraft::class.java, database.getRecordMaps(CentralDatabase.TABLE_DRAFTS) as List<Map<String, Any?>>)
                                8 -> exports["search_history.json"] = ConfigFileUtility.exportToJson(SearchHistory::class.java, database.getRecordMaps(SearchHistory::class.java) as List<Map<String, Any?>>)
                                9 -> exports["used_tags.json"] = Gson().toJson(UsedHashes(applicationContext).all)
                                10 -> exports["stream_channel_states.json"] = ConfigFileUtility.exportToJson(StreamChannelState::class.java, database.getRecordMaps(CentralDatabase.TABLE_STREAM_CHANNEL_STATES) as List<Map<String, Any?>>)
                                11 -> exports["prefs.json"] = Gson().toJson(PreferenceManager.getDefaultSharedPreferences(applicationContext).all)
                            }
                        }

                        exports.forEach { entry ->
                            when (spLocation.selectedItemPosition) {
                                0 -> {
                                    // export to file
                                    val directory = File(Environment.getExternalStorageDirectory(), "Yukari4a")
                                    if (!directory.exists()) {
                                        directory.mkdirs()
                                    }

                                    val file = File(directory, entry.key)
                                    FileOutputStream(file).use {
                                        it.writer().use {
                                            it.write(entry.value)
                                            it.flush()
                                        }
                                    }
                                }
                                1 -> {
                                    // export to Google Drive
                                }
                            }
                        }

                        return null
                    }

                    override fun onPreExecute() {
                        super.onPreExecute()
                        progressFragment = MaintenanceActivity.SimpleProgressDialogFragment.newInstance(null, "エクスポート中...", true, false)
                        progressFragment?.show(supportFragmentManager, "progress")
                    }

                    override fun onPostExecute(result: Void?) {
                        super.onPostExecute(result)
                        progressFragment?.dismiss()
                        progressFragment = null
                        SimpleAlertDialogFragment.newInstance(DIALOG_EXPORT_FINISHED,
                                "エクスポート完了", "エクスポートが完了しました。",
                                "OK", null)
                                .show(supportFragmentManager, "export_finished")
                    }
                }.execute()
            }
        }
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onDeniedWriteExternalStorage() {
        if (PermissionUtils.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showToast("ストレージにアクセスする権限がありません。")
        } else {
            SimpleAlertDialogFragment.newInstance(DIALOG_PERMISSION_DENIED,
                    "許可が必要",
                    "この操作を実行するためには、手動で設定画面からストレージへのアクセスを許可する必要があります。",
                    "設定画面へ",
                    "キャンセル")
                    .show(supportFragmentManager, "permission_denied")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_IMPORT_FINISHED -> {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val setAlarm: AlarmManager.(Int, Long, PendingIntent) -> Unit
                        = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                            AlarmManager::set
                        } else {
                            AlarmManager::setExact
                        }
                alarmManager.setAlarm(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000,
                        PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), 0))
                moveTaskToBack(true)
                Process.killProcess(Process.myPid())
            }
            DIALOG_PERMISSION_DENIED -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
            }
        }
    }

    class BackupFragment : ListFragment() {
        val checkedStates = SparseArrayCompat<Boolean>()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val activity = requireActivity()

            val options = activity.resources.getStringArray(R.array.backup_options)

            savedInstanceState?.let {
                for (i in options.indices) {
                    checkedStates[i] = savedInstanceState.getBoolean(i.toString(), false)
                }
            }

            listAdapter = BackupOptionListAdapter(activity, options)
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            checkedStates.forEach { i, b -> outState.putBoolean(i.toString(), b) }
        }

        override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
            if (position == 0) {
                val newState = !checkedStates.get(0, false)
                for (i in 0..listAdapter.count - 1) {
                    checkedStates[i] = newState
                }
            } else {
                checkedStates[position] = !checkedStates.get(position, false)
            }
            (listAdapter as BackupOptionListAdapter).notifyDataSetChanged()
        }

        private inner class BackupOptionListAdapter(context: Context, objects: Array<String>) : ArrayAdapter<String>(context, 0, objects) {
            private val inflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
                val view = convertView ?: inflater.inflate(R.layout.row_check, null)
                val viewHolder = view.tag as? ViewHolder ?: ViewHolder(view).apply {
                    view.tag = this
                }

                viewHolder.checkBox.text = getItem(position)
                viewHolder.checkBox.isChecked = checkedStates.get(position, false)

                return view
            }
        }

        private class ViewHolder(view: View) {
            val checkBox: CheckBox = view.findViewById(R.id.checkBox) as CheckBox
        }
    }
}