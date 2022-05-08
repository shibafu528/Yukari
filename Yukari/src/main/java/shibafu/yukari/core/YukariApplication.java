package shibafu.yukari.core;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.squareup.leakcanary.LeakCanary;
import shibafu.yukari.R;
import shibafu.yukari.common.NotificationChannelPrefix;
import twitter4j.AlternativeHttpClientImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shibafu on 2015/08/29.
 */
public class YukariApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ProviderInstaller.installIfNeeded(this);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException ignore) {}

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_ipv6", false)) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_force_http1", false)) {
            AlternativeHttpClientImpl.sPreferHttp2 = false;
            AlternativeHttpClientImpl.sPreferSpdy = false;
        }

        // 通知チャンネルの作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel generalChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_general),
                    "その他の通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(generalChannel);

            NotificationChannel errorChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_error),
                    "エラーの通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(errorChannel);

            NotificationChannel coreServiceChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_core_service),
                    "常駐サービス",
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(coreServiceChannel);

            NotificationChannel asyncActionChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id_async_action),
                    "バックグラウンド処理",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(asyncActionChannel);

            createAllAccountNotificationChannels(nm);
        }

        migratePreference();

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        MultiDex.install(this);
    }

    /**
     * すべてのアカウント向けの共通通知チャンネルを生成します。
     * @param nm NotificationManager
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createAllAccountNotificationChannels(@NonNull NotificationManager nm) {
        List<NotificationChannel> channels = new ArrayList<>();
        final AudioAttributes audioAttributes = new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_NOTIFICATION).build();
        final String groupId = NotificationChannelPrefix.GROUP_ACCOUNT + "all";

        NotificationChannelGroup group = new NotificationChannelGroup(groupId, "すべてのアカウント");
        nm.createNotificationChannelGroup(group);

        // Mention
        NotificationChannel mentionChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + "all", "メンション通知", NotificationManager.IMPORTANCE_HIGH);
        mentionChannel.setGroup(groupId);
        mentionChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes);
        mentionChannel.setDescription("@付き投稿の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(mentionChannel);

        // Repost (RT, Boost)
        NotificationChannel repostChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + "all", "リツイート・ブースト通知", NotificationManager.IMPORTANCE_HIGH);
        repostChannel.setGroup(groupId);
        repostChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_rt"), audioAttributes);
        repostChannel.setDescription("あなたの投稿がリツイート・ブーストされた時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(repostChannel);

        // Favorite
        NotificationChannel favoriteChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + "all", "お気に入り通知", NotificationManager.IMPORTANCE_HIGH);
        favoriteChannel.setGroup(groupId);
        favoriteChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_fav"), audioAttributes);
        favoriteChannel.setDescription("あなたの投稿がお気に入り登録された時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(favoriteChannel);

        // Message
        NotificationChannel messageChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + "all", "メッセージ通知", NotificationManager.IMPORTANCE_HIGH);
        messageChannel.setGroup(groupId);
        messageChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes);
        messageChannel.setDescription("あなた宛のメッセージを受信した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(messageChannel);

        // Repost Respond (RT-Respond)
        NotificationChannel repostRespondChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + "all", "RTレスポンス通知", NotificationManager.IMPORTANCE_HIGH);
        repostRespondChannel.setGroup(groupId);
        repostRespondChannel.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), audioAttributes);
        repostRespondChannel.setDescription("あなたの投稿がリツイート・ブーストされ、その直後に感想文らしき投稿を発見した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(repostRespondChannel);

        nm.createNotificationChannels(channels);
    }

    /**
     * マイグレーションの必要な設定を検出し、修正します。
     */
    private void migratePreference() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        // ver 3.0.0
        {
            SharedPreferences.Editor edit = sp.edit();

            // 文字サイズの設定
            if (sp.contains("pref_font_timeline") && !sp.getBoolean("pref_font_timeline__migrate_3_0_0", false)) {
                int size = (int) (Integer.parseInt(sp.getString("pref_font_timeline", "14")) * 0.8);
                edit.putString("pref_font_timeline", String.valueOf(size));
            }
            edit.putBoolean("pref_font_timeline__migrate_3_0_0", true);

            // 入力文字サイズの設定
            if (sp.contains("pref_font_input") && !sp.getBoolean("pref_font_input__migrate_3_0_0", false)) {
                int size = (int) (Integer.parseInt(sp.getString("pref_font_input", "18")) * 0.8);
                edit.putString("pref_font_input", String.valueOf(size));
            }
            edit.putBoolean("pref_font_input__migrate_3_0_0", true);

            edit.apply();
        }
    }
}
