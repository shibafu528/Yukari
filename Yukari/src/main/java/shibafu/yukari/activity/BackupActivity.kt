package shibafu.yukari.activity

import android.Manifest
import android.app.Activity
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
import android.support.v4.app.ListFragment
import android.support.v4.provider.DocumentFile
import android.support.v4.util.SparseArrayCompat
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.PermissionUtils
import permissions.dispatcher.RuntimePermissions
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.NotificationChannelPrefix
import shibafu.yukari.common.TabInfo
import shibafu.yukari.common.UsedHashes
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
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.util.LOG_TAG
import shibafu.yukari.util.forEach
import shibafu.yukari.util.set
import shibafu.yukari.util.showToast
import twitter4j.auth.AccessToken
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

/**
 * Created by shibafu on 2016/03/13.
 */
@RuntimePermissions
class BackupActivity : ActionBarYukariBase(), SimpleAlertDialogFragment.OnDialogChoseListener, CoroutineScope {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODE_IMPORT = 0
        const val EXTRA_MODE_EXPORT = 1

        private const val FRAGMENT_TAG = "backup"

        private const val DIALOG_IMPORT_FINISHED = 1
        private const val DIALOG_EXPORT_FINISHED = 2
        private const val DIALOG_PERMISSION_DENIED = 3
        private const val DIALOG_IMPORT_FAILED = 4
        private const val DIALOG_EXPORT_WARNING = 5

        private const val REQUEST_OPEN_DOCUMENT_TREE = 1
    }

    private val job = Job()

    private lateinit var spLocation: Spinner

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

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

        spLocation = findViewById(R.id.spinner)

        val btnExecute = findViewById<Button>(R.id.btnExecute)
        btnExecute.setOnClickListener { onClickExecuteWithPermissionCheck() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnExecute.text = "フォルダを選択して実行"
        }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onClickExecute() {
        val driver = when (spLocation.selectedItemPosition) {
            0 -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED || Environment.getExternalStorageDirectory() == null) {
                        showToast("SDカードが挿入されていないか、ストレージ領域を正しく認識できていません。")
                        return
                    }
                } else {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_OPEN_DOCUMENT_TREE)
                    return
                }
                FileDriver(File(Environment.getExternalStorageDirectory(), "Yukari4a"))
            }
            else -> throw RuntimeException("invalid location choose")
        }

        executeInternal(driver)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OPEN_DOCUMENT_TREE -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }

                val uri = data?.data ?: run {
                    showToast("フォルダを開けませんでした")
                    return
                }
                val dir = DocumentFile.fromTreeUri(applicationContext, uri) ?: run {
                    showToast("フォルダを開けませんでした")
                    return
                }

                if (intent.getIntExtra(EXTRA_MODE, EXTRA_MODE_EXPORT) == EXTRA_MODE_EXPORT) {
                    // ファイルがごっちゃになる事故回避する用の簡易チェック
                    dir.listFiles().forEach { file ->
                        if (file == null) {
                            return@forEach
                        }

                        val name = file.name
                        if (file.isDirectory || (name != null && !name.endsWith(".json"))) {
                            val extras = Bundle().apply {
                                putParcelable("uri", uri)
                            }
                            SimpleAlertDialogFragment.Builder(DIALOG_EXPORT_WARNING)
                                    .setTitle("確認")
                                    .setMessage("エクスポート先フォルダには、Yukariとは無関係のファイルやフォルダがあるようです。\n名前が重複した場合は上書きされますが、本当にエクスポートしてもよろしいですか？")
                                    .setPositive("OK")
                                    .setNegative("キャンセル")
                                    .setExtras(extras)
                                    .build()
                                    .show(supportFragmentManager, "export_warning")
                            return
                        }
                    }
                }

                executeInternal(SAFDriver(applicationContext, dir))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_IMPORT_FINISHED -> {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val pintent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_MUTABLE)
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pintent)
                }
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
            DIALOG_EXPORT_WARNING -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val uri = extras?.getParcelable<Uri>("uri") ?: run {
                        showToast("フォルダを開けませんでした")
                        return
                    }
                    val dir = DocumentFile.fromTreeUri(applicationContext, uri) ?: run {
                        showToast("フォルダを開けませんでした")
                        return
                    }

                    executeInternal(SAFDriver(applicationContext, dir))
                }
            }
        }
    }

    private fun executeInternal(driver: BackupDriver) {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment !is BackupFragment) throw RuntimeException("fragment type mismatched")

        when (intent.getIntExtra(EXTRA_MODE, EXTRA_MODE_EXPORT)) {
            EXTRA_MODE_IMPORT -> launch {
                val progressFragment = MaintenanceActivity.SimpleProgressDialogFragment.newInstance(null, "インポート中...", true, false)
                progressFragment.show(supportFragmentManager, "progress")

                val causedException = importAsync(driver, fragment).await()

                progressFragment.dismiss()

                if (causedException == null) {
                    val message = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        "設定を反映させるため、アプリを再起動します。\nOKをタップした後、そのまましばらくお待ち下さい。"
                    } else {
                        "設定を反映させるため、アプリを再起動します。\nOKをタップするとアプリを終了しますので、ホーム画面から起動しなおしてください。"
                    }
                    SimpleAlertDialogFragment.newInstance(DIALOG_IMPORT_FINISHED,
                            "インポート完了", message,
                            "OK", null)
                            .show(supportFragmentManager, "import_finished")
                } else {
                    Log.e(LOG_TAG, "", causedException)

                    SimpleAlertDialogFragment.newInstance(DIALOG_IMPORT_FAILED,
                            "インポート失敗", "設定のインポート中にエラーが発生しました。",
                            "OK", null)
                            .show(supportFragmentManager, "import_finished")
                }
            }

            EXTRA_MODE_EXPORT -> launch {
                val progressFragment = MaintenanceActivity.SimpleProgressDialogFragment.newInstance(null, "エクスポート中...", true, false)
                progressFragment.show(supportFragmentManager, "progress")

                exportAsync(driver, fragment).await()

                progressFragment.dismiss()
                SimpleAlertDialogFragment.newInstance(DIALOG_EXPORT_FINISHED,
                        "エクスポート完了", "エクスポートが完了しました。",
                        "OK", null)
                        .show(supportFragmentManager, "export_finished")
            }
        }
    }

    private fun importAsync(driver: BackupDriver, fragment: BackupFragment) = async(Dispatchers.IO) {
        val database = getDatabaseAwait() ?: return@async RuntimeException("Can't get database instance")
        database.beginTransaction()
        try {
            fun <F, T : DBRecord> importIntoDatabase(from: Class<F>, to: Class<T>, json: String)
                    = database.importRecordMaps(to, ConfigFileUtility.importFromJson(from, json, database))
            fun <F> importIntoDatabase(from: Class<F>, to: String, json: String)
                    = database.importRecordMaps(to, ConfigFileUtility.importFromJson(from, json, database))

            fragment.checkedStates.forEach { i, b ->
                try {
                    when (i) {
                        1 -> {
                            val accounts = ConfigFileUtility.importFromJson(AuthUserRecord::class.java, driver.readFile("accounts.json"), database)
                            database.importRecordMaps(AuthUserRecord::class.java, accounts)

                            // providers.jsonは古いバージョンからのインポートの場合は存在しない
                            try {
                                val providers = ConfigFileUtility.importFromJson(Provider::class.java, driver.readFile("providers.json"), database)
                                database.importRecordMaps(Provider::class.java, providers)
                            } catch (ignore: FileNotFoundException) {}

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
                                    if (group.id.startsWith(NotificationChannelPrefix.GROUP_ACCOUNT)) {9
                                        nm.deleteNotificationChannelGroup(group.id)
                                    }
                                }
                            }

                            // y4a 2.0以下との互換処理。Twitterアカウントのプロフィール情報を取得する。
                            val twitter = twitterService.getTwitterOrThrow(null)
                            accounts.forEach eachRecord@{
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
                        2 -> importIntoDatabase(TabInfo::class.java, TabInfo::class.java, driver.readFile("tabs.json"))
                        3 -> importIntoDatabase(MuteConfig::class.java, MuteConfig::class.java, driver.readFile("mute.json"))
                        4 -> importIntoDatabase(AutoMuteConfig::class.java, AutoMuteConfig::class.java, driver.readFile("automute.json"))
                        5 -> importIntoDatabase(UserExtras::class.java, UserExtras::class.java, driver.readFile("user_extras.json"))
                        6 -> importIntoDatabase(Bookmark.SerializeEntity::class.java, Bookmark::class.java, driver.readFile("bookmarks.json"))
                        7 -> importIntoDatabase(StatusDraft::class.java, CentralDatabase.TABLE_DRAFTS, driver.readFile("drafts.json"))
                        8 -> importIntoDatabase(SearchHistory::class.java, SearchHistory::class.java, driver.readFile("draftsearch_history.json"))
                        9 -> {
                            val records: List<String> = Gson().fromJson(driver.readFile("used_tags.json"), object : TypeToken<List<String>>() {}.type)
                            val usedHashes = UsedHashes(applicationContext)
                            records.forEach { usedHashes.put(it) }
                            usedHashes.save(applicationContext)
                        }
                        10 -> importIntoDatabase(StreamChannelState::class.java, StreamChannelState::class.java, driver.readFile("stream_channel_states.json"))
                        11 -> {
                            val records: Map<String, Any?> = Gson().fromJson(driver.readFile("prefs.json"), object : TypeToken<Map<String, Any?>>() {}.type)
                            val spEdit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()

                            // マイグレーションフラグを削除する (インポートしたデータがそれらのマイグレーションを実施済であれば、それも入っているはずなので)
                            spEdit.remove("pref_font_timeline__migrate_3_0_0")
                            spEdit.remove("pref_font_input__migrate_3_0_0")

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
        } catch (e: Exception) {
            return@async e
        } finally {
            database.endTransaction()
        }
        null
    }

    private fun exportAsync(driver: BackupDriver, fragment: BackupFragment) = async(Dispatchers.IO) {
        val database = getDatabaseAwait() ?: return@async RuntimeException("Can't get database instance")
        val exports = mutableMapOf<String, String>()
        fragment.checkedStates.forEach { i, b ->
            @Suppress("UNCHECKED_CAST")
            when (i) {
                1 -> {
                    exports["accounts.json"] = ConfigFileUtility.exportToJson(AuthUserRecord::class.java, database.getRecordMaps(AuthUserRecord::class.java) as List<Map<String, Any?>>)
                    exports["providers.json"] = ConfigFileUtility.exportToJson(Provider::class.java, database.getRecordMaps(Provider::class.java) as List<Map<String, Any?>>)
                }
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
            driver.writeFile(entry.key, entry.value)
        }
    }

    private suspend fun getDatabaseAwait(): CentralDatabase? {
        var count = 0
        while (!isTwitterServiceBound || twitterService == null || twitterService?.database == null) {
            delay(100)
            if (++count >= 30) {
                return null
            }
        }

        return twitterService.database
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

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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

    private interface BackupDriver {
        fun readFile(fileName: String): String
        fun writeFile(fileName: String, data: String)
    }

    /**
     * Filesystem driver for Android 5.0+
     */
    private class SAFDriver(private val context: Context, private val directory: DocumentFile) : BackupDriver {
        override fun readFile(fileName: String): String {
            val file = directory.findFile(fileName) ?: throw FileNotFoundException("not found : $fileName")
            context.contentResolver.openInputStream(file.uri).use { input ->
                if (input == null) {
                    throw IOException("Can't open file : $fileName")
                }
                input.reader().use { reader ->
                    return reader.readText()
                }
            }
        }

        override fun writeFile(fileName: String, data: String) {
            val file = directory.findFile(fileName) ?: directory.createFile("application/json", fileName) ?: throw FileNotFoundException("Can't create file : $fileName")
            context.contentResolver.openOutputStream(file.uri).use { output ->
                if (output == null) {
                    throw IOException("Can't create file : $fileName")
                }
                output.writer().use { writer ->
                    writer.write(data)
                    writer.flush()
                }
            }
        }
    }

    /**
     * Filesystem driver for pre Android 5.0
     */
    private class FileDriver(private val directory: File) : BackupDriver {
        init {
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }

        override fun readFile(fileName: String): String {
            val file = File(directory, fileName)
            if (!file.exists()) {
                throw FileNotFoundException("not found : $fileName")
            }
            FileInputStream(file).use { input ->
                input.reader().use { reader ->
                    return reader.readText()
                }
            }
        }

        override fun writeFile(fileName: String, data: String) {
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                output.writer().use { writer ->
                    writer.write(data)
                    writer.flush()
                }
            }
        }
    }
}