package shibafu.yukari.linkage;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.res.ResourcesCompat;
import android.util.Log;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.NotificationChannelPrefix;
import shibafu.yukari.common.NotificationPreferenceSoundUri;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.TabType;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.User;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.util.CompatUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by shibafu on 2015/07/27.
 */
public class StatusNotifier {
    private static final String LOG_TAG = "StatusNotifier";

    //バイブレーションパターン
    private final static long[] VIB_REPLY = {450, 130, 140, 150};
    private final static long[] VIB_RETWEET = {150, 130, 300, 150};
    private final static long[] VIB_FAVED = {140, 100};

    private Context context;
    private Handler handler;
    private SharedPreferences sharedPreferences;
    private NotificationManager notificationManager;
    private AudioManager audioManager;
    private Vibrator vibrator;

    public StatusNotifier(Context context) {
        this.context = context.getApplicationContext();

        this.handler = new Handler();

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private Uri getNotificationUrl(int category) {
        switch (category) {
            case R.integer.notification_replied:
                return NotificationPreferenceSoundUri.parse(sharedPreferences.getString("pref_notif_mention_sound_uri", null));
            case R.integer.notification_message:
                return NotificationPreferenceSoundUri.parse(sharedPreferences.getString("pref_notif_dm_sound_uri", null));
            case R.integer.notification_respond:
                return NotificationPreferenceSoundUri.parse(sharedPreferences.getString("pref_notif_respond_sound_uri", null));
            case R.integer.notification_retweeted:
                return NotificationPreferenceSoundUri.parse(sharedPreferences.getString("pref_notif_rt_sound_uri", null));
            case R.integer.notification_faved:
                return NotificationPreferenceSoundUri.parse(sharedPreferences.getString("pref_notif_fav_sound_uri", null));
            default:
                return null;
        }
    }

    public void showNotification(int category, Status status, User actionBy) {
        int prefValue = 5;
        switch (category) {
            case R.integer.notification_replied:
                prefValue = sharedPreferences.getInt("pref_notif_mention", 5);
                break;
            case R.integer.notification_retweeted:
                prefValue = sharedPreferences.getInt("pref_notif_rt", 5);
                break;
            case R.integer.notification_faved:
                prefValue = sharedPreferences.getInt("pref_notif_fav", 5);
                break;
            case R.integer.notification_message:
                prefValue = sharedPreferences.getInt("pref_notif_dm", 5);
                break;
            case R.integer.notification_respond:
                prefValue = sharedPreferences.getInt("pref_notif_respond", 0);
                break;
        }
        NotificationType notificationType = new NotificationType(prefValue);

        if (notificationType.isEnabled()) {
            int icon = 0, color = ResourcesCompat.getColor(context.getResources(), R.color.key_color, null);
            Uri sound = getNotificationUrl(category);
            String titleHeader = "", tickerHeader = "";
            long[] pattern = null;
            String channelId;
            String channelIdSuffix;
            if (sharedPreferences.getBoolean("pref_notif_per_account_channel", false)) {
                channelIdSuffix = status.getRepresentUser().Url;
            } else {
                channelIdSuffix = "all";
            }
            switch (category) {
                case R.integer.notification_replied:
                    icon = R.drawable.ic_stat_reply;
                    titleHeader = "Reply from @";
                    tickerHeader = "リプライ : @";
                    pattern = VIB_REPLY;
                    channelId = NotificationChannelPrefix.CHANNEL_MENTION + channelIdSuffix;
                    break;
                case R.integer.notification_retweeted:
                    icon = R.drawable.ic_stat_retweet;
                    titleHeader = "Boosted by @";
                    tickerHeader = "BTされました : @";
                    pattern = VIB_RETWEET;
                    color = Color.rgb(0, 128, 0);
                    channelId = NotificationChannelPrefix.CHANNEL_REPOST + channelIdSuffix;
                    break;
                case R.integer.notification_faved:
                    icon = R.drawable.ic_stat_favorite;
                    titleHeader = "Faved by @";
                    tickerHeader = "ふぁぼられ : @";
                    pattern = VIB_FAVED;
                    color = Color.rgb(255, 128, 0);
                    channelId = NotificationChannelPrefix.CHANNEL_FAVORITE + channelIdSuffix;
                    break;
                case R.integer.notification_message:
                    icon = R.drawable.ic_stat_message;
                    titleHeader = "Message from @";
                    tickerHeader = "DM : @";
                    pattern = VIB_REPLY;
                    channelId = NotificationChannelPrefix.CHANNEL_MESSAGE + channelIdSuffix;
                    break;
                case R.integer.notification_respond:
                    icon = R.drawable.ic_stat_reply;
                    titleHeader = "BT-Respond from @";
                    tickerHeader = "BTレスポンス : @";
                    pattern = VIB_REPLY;
                    channelId = NotificationChannelPrefix.CHANNEL_REPOST_RESPOND + channelIdSuffix;
                    break;
                default:
                    throw new IllegalArgumentException("Undefined notification category " + category);
            }
            if (notificationType.getNotificationType() == NotificationType.TYPE_NOTIF) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext(), channelId);
                builder.setSmallIcon(icon);
                builder.setContentTitle(titleHeader + actionBy.getScreenName());
                builder.setContentText(status.getOriginStatus().getTextWithoutMentions());
                builder.setContentIntent(CompatUtil.getEmptyPendingIntent(context));
                builder.setTicker(tickerHeader + actionBy.getScreenName());
                builder.setColor(color);
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                builder.setAutoCancel(true);
                switch (category) {
                    case R.integer.notification_replied:
                    {
                        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                context.getApplicationContext(), R.integer.notification_replied, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        builder.setContentIntent(pendingIntent);

                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            replyIntent.putExtra(TweetActivity.EXTRA_STATUS, status.getOriginStatus());
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            replyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                    status.getOriginStatus().getUser().getScreenName() + " ");
                            builder.addAction(R.drawable.ic_stat_reply, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_replied, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                            );
                        }
                        {
                            Intent voiceReplyIntent = new Intent(context.getApplicationContext(), PostService.class);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_STATUS, status.getOriginStatus());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                    status.getOriginStatus().getUser().getScreenName() + " ");
                            NotificationCompat.Action voiceReply = new NotificationCompat.Action
                                    .Builder(R.drawable.ic_stat_reply, "声で返信",
                                    PendingIntent.getService(context.getApplicationContext(),
                                            R.integer.notification_replied,
                                            voiceReplyIntent,
                                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE))
                                    .addRemoteInput(new RemoteInput.Builder(PostService.EXTRA_REMOTE_INPUT).setLabel("返信").build())
                                    .build();
                            builder.extend(new NotificationCompat.WearableExtender().addAction(voiceReply));
                        }
                        break;
                    }
                    case R.integer.notification_respond:
                    {
                        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                context.getApplicationContext(), R.integer.notification_respond, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        builder.setContentIntent(pendingIntent);
                        break;
                    }
                    case R.integer.notification_message:
                    {
                        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_DM);
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                context.getApplicationContext(), R.integer.notification_message, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        builder.setContentIntent(pendingIntent);

                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            replyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, TwitterUtil.getUrlFromUserId(status.getUser().getId()));
                            replyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, status.getUser().getScreenName());
                            builder.addAction(R.drawable.ic_stat_message, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_message, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                            );
                        }
                        {
                            Intent voiceReplyIntent = new Intent(context.getApplicationContext(), PostService.class);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, TwitterUtil.getUrlFromUserId(status.getUser().getId()));
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, status.getUser().getScreenName());
                            NotificationCompat.Action voiceReply = new NotificationCompat.Action
                                    .Builder(R.drawable.ic_stat_reply, "声で返信",
                                    PendingIntent.getService(context.getApplicationContext(),
                                            R.integer.notification_replied,
                                            voiceReplyIntent,
                                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE))
                                    .addRemoteInput(new RemoteInput.Builder(PostService.EXTRA_REMOTE_INPUT).setLabel("返信").build())
                                    .build();
                            builder.extend(new NotificationCompat.WearableExtender().addAction(voiceReply));
                        }
                        break;
                    }
                }
                notificationManager.notify(category, builder.build());
            }
            else {
                if (notificationType.isUseSound() && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && sound != null) {
                    try {
                        MediaPlayer mediaPlayer = new MediaPlayer();
                        mediaPlayer.setDataSource(context, sound);
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    } catch (IllegalStateException | IOException e) {
                        e.printStackTrace();
                    }
                }
                if (notificationType.isUseVibration()) {
                    switch (audioManager.getRingerMode()) {
                        case AudioManager.RINGER_MODE_NORMAL:
                        case AudioManager.RINGER_MODE_VIBRATE:
                            vibrator.vibrate(pattern, -1);
                            break;
                    }
                }
                final String text = tickerHeader + actionBy.getScreenName() + "\n" + status.getOriginStatus().getTextWithoutMentions();
                handler.post(() -> Toast.makeText(context.getApplicationContext(),
                        text,
                        Toast.LENGTH_LONG)
                        .show());
            }
        }
    }
}
