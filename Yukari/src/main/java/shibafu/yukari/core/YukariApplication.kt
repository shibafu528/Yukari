package shibafu.yukari.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.squareup.leakcanary.LeakCanary
import shibafu.yukari.R
import shibafu.yukari.common.NotificationChannelPrefix
import twitter4j.AlternativeHttpClientImpl

/**
 * Created by shibafu on 2015/08/29.
 */
class YukariApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (ignore: GooglePlayServicesRepairableException) {
        } catch (ignore: GooglePlayServicesNotAvailableException) {
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            System.setProperty("java.net.preferIPv4Stack", "true")
            System.setProperty("java.net.preferIPv6Addresses", "false")
        }
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_force_http1", false)) {
            AlternativeHttpClientImpl.sPreferHttp2 = false
            AlternativeHttpClientImpl.sPreferSpdy = false
        }

        // 通知チャンネルの作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val generalChannel = NotificationChannel(
                    getString(R.string.notification_channel_id_general),
                    "その他の通知",
                    NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(generalChannel)
            val errorChannel = NotificationChannel(
                    getString(R.string.notification_channel_id_error),
                    "エラーの通知",
                    NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(errorChannel)
            val coreServiceChannel = NotificationChannel(
                    getString(R.string.notification_channel_id_core_service),
                    "常駐サービス",
                    NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(coreServiceChannel)
            val asyncActionChannel = NotificationChannel(
                    getString(R.string.notification_channel_id_async_action),
                    "バックグラウンド処理",
                    NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(asyncActionChannel)
            createAllAccountNotificationChannels(nm)
        }

        migratePreference()

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }
        LeakCanary.install(this)
    }

    /**
     * すべてのアカウント向けの共通通知チャンネルを生成します。
     * @param nm NotificationManager
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAllAccountNotificationChannels(nm: NotificationManager) {
        val channels: MutableList<NotificationChannel> = ArrayList()
        val audioAttributes = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_NOTIFICATION).build()
        val groupId = NotificationChannelPrefix.GROUP_ACCOUNT + "all"

        val group = NotificationChannelGroup(groupId, "すべてのアカウント")
        nm.createNotificationChannelGroup(group)

        // Mention
        val mentionChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + "all", "メンション通知", NotificationManager.IMPORTANCE_HIGH)
        mentionChannel.group = groupId
        mentionChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes)
        mentionChannel.description = "@付き投稿の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(mentionChannel)

        // Repost (RT, Boost)
        val repostChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + "all", "リツイート・ブースト通知", NotificationManager.IMPORTANCE_HIGH)
        repostChannel.group = groupId
        repostChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_rt"), audioAttributes)
        repostChannel.description = "あなたの投稿がリツイート・ブーストされた時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(repostChannel)

        // Favorite
        val favoriteChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + "all", "お気に入り通知", NotificationManager.IMPORTANCE_HIGH)
        favoriteChannel.group = groupId
        favoriteChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_fav"), audioAttributes)
        favoriteChannel.description = "あなたの投稿がお気に入り登録された時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(favoriteChannel)

        // Message
        val messageChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + "all", "メッセージ通知", NotificationManager.IMPORTANCE_HIGH)
        messageChannel.group = groupId
        messageChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes)
        messageChannel.description = "あなた宛のメッセージを受信した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(messageChannel)

        // Repost Respond (RT-Respond)
        val repostRespondChannel = NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + "all", "RTレスポンス通知", NotificationManager.IMPORTANCE_HIGH)
        repostRespondChannel.group = groupId
        repostRespondChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes)
        repostRespondChannel.description = "あなたの投稿がリツイート・ブーストされ、その直後に感想文らしき投稿を発見した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！"
        channels.add(repostRespondChannel)

        nm.createNotificationChannels(channels)
    }

    /**
     * マイグレーションの必要な設定を検出し、修正します。
     */
    private fun migratePreference() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        // ver 3.0.0
        sp.edit {
            // 文字サイズの設定
            if (sp.contains("pref_font_timeline") && !sp.getBoolean("pref_font_timeline__migrate_3_0_0", false)) {
                val size = (sp.getString("pref_font_timeline", "14")!!.toInt() * 0.8).toInt()
                putString("pref_font_timeline", size.toString())
            }
            putBoolean("pref_font_timeline__migrate_3_0_0", true)

            // 入力文字サイズの設定
            if (sp.contains("pref_font_input") && !sp.getBoolean("pref_font_input__migrate_3_0_0", false)) {
                val size = (sp.getString("pref_font_input", "18")!!.toInt() * 0.8).toInt()
                putString("pref_font_input", size.toString())
            }
            putBoolean("pref_font_input__migrate_3_0_0", true)
        }
    }
}