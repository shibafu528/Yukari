package shibafu.yukari.activity

import android.content.Context
import android.os.Bundle
import android.support.v4.app.ListFragment
import android.support.v4.util.SparseArrayCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import butterknife.ButterKnife
import butterknife.InjectView
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.util.forEach
import shibafu.yukari.util.set
import shibafu.yukari.util.showToast

/**
 * Created by shibafu on 2016/03/13.
 */
class BackupActivity : ActionBarYukariBase() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODE_IMPORT = 0
        const val EXTRA_MODE_EXPORT = 1
    }

    @InjectView(R.id.spinner) lateinit var spLocation: Spinner
    @InjectView(R.id.tvLocation) lateinit var tvLocation: TextView

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
                .replace(R.id.frame, BackupFragment())
                .commit()
        }

        ButterKnife.inject(this)
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    class BackupFragment : ListFragment() {
        private val checkedStates = SparseArrayCompat<Boolean>()

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
            checkedStates[position] = !checkedStates.get(position, false)
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