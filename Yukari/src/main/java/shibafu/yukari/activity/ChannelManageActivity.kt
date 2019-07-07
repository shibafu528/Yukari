package shibafu.yukari.activity

import android.content.Context
import android.os.Bundle
import android.support.v4.app.ListFragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.service.TwitterServiceConnection
import shibafu.yukari.service.TwitterServiceDelegate

class ChannelManageActivity : ActionBarYukariBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<LinearLayout>(R.id.llFrameTitle).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvFrameTitle).text = "ストリーミング通信(または自動取得)を有効にしたいチャンネルにチェックを入れてください。\nチャンネルは各アカウントごとに受信する内容(または自動取得の方式)で分かれています。"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, ChannelListFragment(), "list")
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onServiceConnected() {
        val fragment = supportFragmentManager.findFragmentByTag("list") as? ChannelListFragment
        fragment?.onServiceConnected()
    }

    override fun onServiceDisconnected() {}

    class ChannelListFragment : ListFragment(), TwitterServiceConnection.ServiceConnectionCallback {
        private lateinit var serviceDelegate: TwitterServiceDelegate

        override fun onAttach(context: Context?) {
            super.onAttach(context)
            serviceDelegate = context as TwitterServiceDelegate
        }

        override fun onServiceConnected() {
            createList()
        }

        override fun onServiceDisconnected() {}

        override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
            val channel = listAdapter.getItem(position) as StreamChannel
            val userRecord = serviceDelegate.twitterService.users.first { it == channel.userRecord }
            val state = serviceDelegate.twitterService.database.getRecords(StreamChannelState::class.java,
                    CentralDatabase.COL_STREAM_CHANNEL_STATES_ACCOUNT_ID + " = ? AND " + CentralDatabase.COL_STREAM_CHANNEL_STATES_CHANNEL_ID + " = ?",
                    arrayOf(userRecord.InternalId.toString(), channel.channelId)).firstOrNull()
                    ?: StreamChannelState(userRecord.InternalId, channel.channelId, false)
            if (channel.isRunning) {
                channel.stop()
                state.isActive = false
            } else {
                channel.start()
                state.isActive = true
            }
            serviceDelegate.twitterService.database.updateRecord(state)
            createList()
        }

        private fun createList() {
            val channelList = serviceDelegate.twitterService.providerStreams
                    .flatMap { it?.channels ?: emptyList() }
                    .filter { it.allowUserControl }

            var adapter = listAdapter as? ChannelListAdapter
            if (adapter == null) {
                adapter = ChannelListAdapter(requireContext(), channelList.toMutableList())
                listAdapter = adapter
            } else {
                adapter.clear()
                adapter.addAll(channelList)
                // ArrayAdapterの内部で呼ばれてる？
                //adapter.notifyDataSetChanged()
            }
        }

        private class ChannelListAdapter(context: Context, list: List<StreamChannel>) : ArrayAdapter<StreamChannel>(context, 0, list) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                lateinit var view: View
                lateinit var viewHolder: ChannelViewHolder
                if (convertView == null) {
                    view = inflater.inflate(R.layout.row_stream_channel, parent, false)
                    viewHolder = ChannelViewHolder(
                            view.findViewById(R.id.user_icon) as ImageView,
                            view.findViewById(R.id.user_name) as TextView,
                            view.findViewById(R.id.user_sn) as TextView,
                            view.findViewById(R.id.user_channel) as TextView,
                            view.findViewById(R.id.user_color) as ImageView,
                            view.findViewById(R.id.user_check) as CheckBox
                    )
                    view.tag = viewHolder
                } else {
                    view = convertView
                    viewHolder = convertView.tag as ChannelViewHolder
                }

                val item = getItem(position)
                if (item != null) {
                    viewHolder.tvName.text = item.userRecord.Name
                    viewHolder.tvScreenName.text = "@" + item.userRecord.ScreenName
                    viewHolder.tvChannelName.text = item.channelName
                    viewHolder.ivAccountColor.setBackgroundColor(item.userRecord.AccountColor)
                    ImageLoaderTask.loadProfileIcon(context, viewHolder.ivIcon, item.userRecord.ProfileImageUrl)
                    viewHolder.chkActive.isChecked = item.isRunning
                }

                return view
            }
        }

        private data class ChannelViewHolder(
                val ivIcon: ImageView,
                val tvName: TextView,
                val tvScreenName: TextView,
                val tvChannelName: TextView,
                val ivAccountColor: ImageView,
                val chkActive: CheckBox
        )
    }
}