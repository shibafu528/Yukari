package shibafu.yukari.database;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.common.NotificationChannelPrefix;

public interface AccountManager {
    void reloadUsers();

    @NonNull
    List<AuthUserRecord> getUsers();

    @Nullable
    AuthUserRecord getPrimaryUser();

    void setPrimaryUser(long id);

    ArrayList<AuthUserRecord> getWriterUsers();

    void setWriterUsers(List<AuthUserRecord> writers);

    void setUserColor(long id, int color);

    void storeUsers();

    void deleteUser(long id);

    @Nullable
    AuthUserRecord findPreferredUser(@ApiType int apiType);

    //<editor-fold desc="UserExtras">
    void setColor(String url, int color);

    void setPriority(String url, AuthUserRecord userRecord);

    @Nullable
    AuthUserRecord getPriority(String url);

    List<UserExtras> getUserExtras();
    //</editor-fold>

    /**
     * アカウントに対応する通知チャンネルを生成します。
     * @param nm NotificationManager
     * @param userRecord アカウント情報
     * @param forceReplace 既に存在する場合に作り直すか？
     */
    @RequiresApi(Build.VERSION_CODES.O)
    static void createAccountNotificationChannels(@NonNull NotificationManager nm, @NonNull AuthUserRecord userRecord, boolean forceReplace) {
        List<NotificationChannel> channels = new ArrayList<>();
        final AudioAttributes audioAttributes = new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_NOTIFICATION).build();
        final String groupId = NotificationChannelPrefix.GROUP_ACCOUNT + userRecord.Url;

        NotificationChannelGroup group = new NotificationChannelGroup(groupId, userRecord.ScreenName);
        nm.createNotificationChannelGroup(group);

        // Mention
        NotificationChannel mentionChannel = nm.getNotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + userRecord.Url);
        if (mentionChannel == null) {
            mentionChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + userRecord.Url, "メンション通知", NotificationManager.IMPORTANCE_HIGH);
        } else if (forceReplace) {
            nm.deleteNotificationChannel(NotificationChannelPrefix.CHANNEL_MENTION + userRecord.Url);
        }
        mentionChannel.setGroup(groupId);
        mentionChannel.setDescription("@付き投稿の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(mentionChannel);

        // Repost (RT, Boost)
        NotificationChannel repostChannel = nm.getNotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + userRecord.Url);
        if (repostChannel == null) {
            repostChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + userRecord.Url, "リツイート・ブースト通知", NotificationManager.IMPORTANCE_HIGH);
        } else if (forceReplace) {
            nm.deleteNotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST + userRecord.Url);
        }
        repostChannel.setGroup(groupId);
        repostChannel.setDescription("あなたの投稿がリツイート・ブーストされた時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(repostChannel);

        // Favorite
        NotificationChannel favoriteChannel = nm.getNotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + userRecord.Url);
        if (favoriteChannel == null) {
            favoriteChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + userRecord.Url, "お気に入り通知", NotificationManager.IMPORTANCE_HIGH);
        } else if (forceReplace) {
            nm.deleteNotificationChannel(NotificationChannelPrefix.CHANNEL_FAVORITE + userRecord.Url);
        }
        favoriteChannel.setGroup(groupId);
        favoriteChannel.setDescription("あなたの投稿がお気に入り登録された時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(favoriteChannel);

        // Message
        NotificationChannel messageChannel = nm.getNotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + userRecord.Url);
        if (messageChannel == null) {
            messageChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + userRecord.Url, "メッセージ通知", NotificationManager.IMPORTANCE_HIGH);
        } else if (forceReplace) {
            nm.deleteNotificationChannel(NotificationChannelPrefix.CHANNEL_MESSAGE + userRecord.Url);
        }
        messageChannel.setGroup(groupId);
        messageChannel.setDescription("あなた宛のメッセージを受信した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(messageChannel);

        // Repost Respond (RT-Respond)
        NotificationChannel repostRespondChannel = nm.getNotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND);
        if (repostRespondChannel == null) {
            repostRespondChannel = new NotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + userRecord.Url, "RTレスポンス通知", NotificationManager.IMPORTANCE_HIGH);
        } else if (forceReplace) {
            nm.deleteNotificationChannel(NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + userRecord.Url);
        }
        repostRespondChannel.setGroup(groupId);
        repostRespondChannel.setDescription("あなたの投稿がリツイート・ブーストされ、その直後に感想文らしき投稿を発見した時の通知\n注意: ここで有効にしていても、アプリ内の通知設定を有効にしていないと機能しません！");
        channels.add(repostRespondChannel);

        nm.createNotificationChannels(channels);
    }
}
