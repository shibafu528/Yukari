package shibafu.yukari.activity

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v4.app.ListFragment
import android.support.v4.util.SparseArrayCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Spinner
import com.google.gson.Gson
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.TabInfo
import shibafu.yukari.common.TweetDraft
import shibafu.yukari.common.UsedHashes
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.SearchHistory
import shibafu.yukari.database.UserExtras
import shibafu.yukari.export.ConfigFileUtility
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.forEach
import shibafu.yukari.util.set
import shibafu.yukari.util.showToast
import java.io.File
import java.io.FileOutputStream

/**
 * Created by shibafu on 2016/03/13.
 */
class BackupActivity : ActionBarYukariBase() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODE_IMPORT = 0
        const val EXTRA_MODE_EXPORT = 1

        private const val FRAGMENT_TAG = "backup"
    }

    val spLocation: Spinner by lazy { findViewById(R.id.spinner) as Spinner }

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

        findViewById(R.id.btnExecute).setOnClickListener { onClickExecute() }
    }

    fun onClickExecute() {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment !is BackupFragment) throw RuntimeException("fragment type mismatched")

        when (intent.getIntExtra(EXTRA_MODE, EXTRA_MODE_EXPORT)) {
            EXTRA_MODE_IMPORT -> {

            }
            EXTRA_MODE_EXPORT -> {
                if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED || Environment.getExternalStorageDirectory() == null) {
                    showToast("SDカードが挿入されていないか、ストレージ領域を正しく認識できていません。")
                    return
                }

                val service = twitterService
                val database = service.database

                val exports = mutableMapOf<String, String>()
                fragment.checkedStates.forEach { i, b ->
                    when (i) {
                        1 -> exports["accounts.json"] = ConfigFileUtility.exportToJson(AuthUserRecord::class.java, service.users)
                        2 -> exports["tabs.json"] = ConfigFileUtility.exportToJson(TabInfo::class.java, database.tabs)
                        3 -> exports["mute.json"] = ConfigFileUtility.exportToJson(MuteConfig::class.java, database.getRecords(MuteConfig::class.java))
                        4 -> exports["automute.json"] = ConfigFileUtility.exportToJson(AutoMuteConfig::class.java, database.getRecords(AutoMuteConfig::class.java))
                        5 -> exports["user_extras.json"] = ConfigFileUtility.exportToJson(UserExtras::class.java, database.getRecords(UserExtras::class.java))
                        6 -> exports["bookmarks.json"] = ConfigFileUtility.exportToJson(Bookmark.SerializeEntity::class.java, database.bookmarks.map { it.serialize() })
                        7 -> exports["drafts.json"] = ConfigFileUtility.exportToJson(TweetDraft::class.java, database.drafts)
                        8 -> exports["search_history.json"] = ConfigFileUtility.exportToJson(SearchHistory::class.java, database.searchHistories)
                        9 -> exports["used_tags.json"] = Gson().toJson(UsedHashes(applicationContext).all)
                        10 -> exports["prefs.json"] = Gson().toJson(PreferenceManager.getDefaultSharedPreferences(applicationContext).all)
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

                showToast("設定をエクスポートしました。")
            }
        }
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    class BackupFragment : ListFragment() {
        val checkedStates = SparseArrayCompat<Boolean>()

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val options = activity.resources.getStringArray(R.array.backup_options)

            savedInstanceState?.let {
                for (i in options.indices) {
                    checkedStates[i] = savedInstanceState.getBoolean(i.toString(), false)
                }
            }

            listAdapter = BackupOptionListAdapter(activity, options)
        }

        override fun onSaveInstanceState(outState: Bundle?) {
            super.onSaveInstanceState(outState)
            outState?.let {
                checkedStates.forEach { i, b -> outState.putBoolean(i.toString(), b) }
            }
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