package shibafu.yukari.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringDef
import androidx.core.content.edit
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.NotificationChannelPrefix
import shibafu.yukari.common.NotificationPreferenceSoundUri
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.databinding.ActivityNotificationPreferenceBinding
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.common.NotificationType as ParsedNotificationPreference

class NotificationPreferenceActivity : ActionBarYukariBase(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityNotificationPreferenceBinding

    private val notificationType: String?
        get() = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE)

    private val notificationSoundPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            defaultSharedPreferences.edit {
                putString("pref_notif_${notificationType}_sound_uri", NotificationPreferenceSoundUri.toString(uri))
            }
        }
    }

    private val accountChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val userRecord = result.data?.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD) as? AuthUserRecord ?: return@registerForActivityResult
            startSystemNotificationActivity(userRecord.Url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationPreferenceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = when (notificationType) {
            NOTIFICATION_MENTION -> "メンション通知"
            NOTIFICATION_RETWEET -> "ブースト通知"
            NOTIFICATION_FAVORITE -> "お気に入り通知"
            NOTIFICATION_DIRECT_MESSAGE -> "メッセージ通知"
            NOTIFICATION_RT_RESPOND -> "BTレスポンス通知"
            else -> throw RuntimeException("invalid notification type")
        }

        binding.swEnabled.setOnCheckedChangeListener { _, isChecked ->
            val preference = getPreference()
            preference.isEnabled = isChecked
            updatePreference(preference)
        }

        binding.rgNotifyMode.setOnCheckedChangeListener { _, checkedId ->
            val preference = getPreference()
            if (checkedId == R.id.rbNotifyStatus) {
                preference.notificationType = ParsedNotificationPreference.TYPE_NOTIF
            } else {
                preference.notificationType = ParsedNotificationPreference.TYPE_TOAST
            }
            updatePreference(preference)
        }

        binding.llNotifyPostOreo.setOnClickListener {
            if (defaultSharedPreferences.getBoolean("pref_notif_per_account_channel", false)) {
                accountChooser.launch(Intent(this@NotificationPreferenceActivity, AccountChooserActivity::class.java))
            } else {
                startSystemNotificationActivity()
            }
        }

        binding.swEnableNotifySound.setOnCheckedChangeListener { _, isChecked ->
            val preference = getPreference()
            preference.isUseSound = isChecked
            updatePreference(preference)
        }

        binding.swEnableNotifyVibration.setOnCheckedChangeListener { _, isChecked ->
            val preference = getPreference()
            preference.isUseVibration = isChecked
            updatePreference(preference)
        }

        binding.tvNotifySound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                val uriString = defaultSharedPreferences.getString("pref_notif_${notificationType}_sound_uri", null)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, NotificationPreferenceSoundUri.parse(uriString))
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            notificationSoundPicker.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        applyPreferenceToView()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "pref_notif_${notificationType}") {
            applyPreferenceToView()
        }
    }

    private fun applyPreferenceToView() {
        val preference = getPreference()
        binding.swEnabled.isChecked = preference.isEnabled
        when (preference.notificationType) {
            ParsedNotificationPreference.TYPE_NOTIF -> binding.rbNotifyStatus.isChecked = true
            ParsedNotificationPreference.TYPE_TOAST -> binding.rbNotifyToast.isChecked = true
        }
        binding.swEnableNotifySound.isChecked = preference.isUseSound
        binding.swEnableNotifyVibration.isChecked = preference.isUseVibration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && preference.notificationType == ParsedNotificationPreference.TYPE_NOTIF) {
            binding.llNotifyPostOreo.visibility = View.VISIBLE
            binding.llNotifyPreOreo.visibility = View.GONE
        } else {
            binding.llNotifyPostOreo.visibility = View.GONE
            binding.llNotifyPreOreo.visibility = View.VISIBLE
        }
    }

    private fun getPreference(): ParsedNotificationPreference {
        val rawValue = when (notificationType) {
            NOTIFICATION_MENTION -> defaultSharedPreferences.getInt("pref_notif_mention", 5)
            NOTIFICATION_RETWEET -> defaultSharedPreferences.getInt("pref_notif_rt", 5)
            NOTIFICATION_FAVORITE -> defaultSharedPreferences.getInt("pref_notif_fav", 5)
            NOTIFICATION_DIRECT_MESSAGE -> defaultSharedPreferences.getInt("pref_notif_dm", 5)
            NOTIFICATION_RT_RESPOND -> defaultSharedPreferences.getInt("pref_notif_respond", 0)
            else -> throw RuntimeException("invalid notification type")
        }
        return ParsedNotificationPreference(rawValue)
    }

    private fun updatePreference(preference: ParsedNotificationPreference) {
        defaultSharedPreferences.edit {
            putInt("pref_notif_${notificationType}", preference.toInteger())
        }
    }

    private fun startSystemNotificationActivity(channelSuffix: String = "all") {
        val channelId = when (notificationType) {
            NOTIFICATION_MENTION -> NotificationChannelPrefix.CHANNEL_MENTION + channelSuffix
            NOTIFICATION_RETWEET -> NotificationChannelPrefix.CHANNEL_REPOST + channelSuffix
            NOTIFICATION_FAVORITE -> NotificationChannelPrefix.CHANNEL_FAVORITE + channelSuffix
            NOTIFICATION_DIRECT_MESSAGE -> NotificationChannelPrefix.CHANNEL_MESSAGE + channelSuffix
            NOTIFICATION_RT_RESPOND -> NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + channelSuffix
            else -> throw RuntimeException("invalid notification type")
        }
        try {
            // EMUI 8.xなど、正攻法で呼び出すと通知音のカスタマイズが行えない
            // ベンダーオリジナルのActivityが起動されることがある。
            // そういうのは嫌なので、素のAndroidの設定画面を指名して呼び出す。
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.setClassName("com.android.settings", "com.android.settings.Settings\$ChannelNotificationSettingsActivity")
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // このブロックをわざわざ用意する意味はないかもしれない、とりあえず正攻法での呼出を書いただけ
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            startActivity(intent)
        }
    }

    @StringDef(
            NOTIFICATION_MENTION,
            NOTIFICATION_RETWEET,
            NOTIFICATION_FAVORITE,
            NOTIFICATION_DIRECT_MESSAGE,
            NOTIFICATION_RT_RESPOND,
    )
    annotation class NotificationType

    companion object {
        const val NOTIFICATION_MENTION = "mention"
        const val NOTIFICATION_RETWEET = "rt"
        const val NOTIFICATION_FAVORITE = "fav"
        const val NOTIFICATION_DIRECT_MESSAGE = "dm"
        const val NOTIFICATION_RT_RESPOND = "respond"

        private const val EXTRA_NOTIFICATION_TYPE = "notificationType"

        @JvmStatic
        fun newIntent(context: Context, @NotificationType notificationType: String): Intent {
            return Intent(context, NotificationPreferenceActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TYPE, notificationType)
            }
        }
    }
}