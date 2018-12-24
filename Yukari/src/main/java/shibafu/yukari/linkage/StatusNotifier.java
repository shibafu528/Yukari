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
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.widget.Toast;
import info.shibafu528.yukari.processor.autorelease.AutoRelease;
import info.shibafu528.yukari.processor.autorelease.AutoReleaser;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.NotificationChannelPrefix;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.TabType;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.User;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.util.CompatUtil;
import shibafu.yukari.util.Releasable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by shibafu on 2015/07/27.
 */
public class StatusNotifier implements Releasable {
    private static final String LOG_TAG = "StatusNotifier";

    //バイブレーションパターン
    private final static long[] VIB_REPLY = {450, 130, 140, 150};
    private final static long[] VIB_RETWEET = {150, 130, 300, 150};
    private final static long[] VIB_FAVED = {140, 100};

    //通知SE リソースUri
    private static final Map<String, SoundEffects> SE_URIS = new HashMap<>();

    private static final String USER_SE_REPLY = "se_reply.wav";
    private static final String USER_SE_RETWEET = "se_retweet.wav";
    private static final String USER_SE_FAVORITE = "se_favorite.wav";

    @AutoRelease TwitterService service;
    @AutoRelease Context context;
    @AutoRelease Handler handler;
    @AutoRelease SharedPreferences sharedPreferences;
    @AutoRelease NotificationManager notificationManager;
    @AutoRelease AudioManager audioManager;
    @AutoRelease Vibrator vibrator;

    private boolean useUserFileOnReply = false;
    private boolean useUserFileOnRetweet = false;
    private boolean useUserFileOnFavorite = false;
    
    static {
        SE_URIS.put("default", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/se_fav");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/se_rt");
        }});
        SE_URIS.put("yukari_fav", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/y_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/y_fav");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/y_rt");
        }});
        SE_URIS.put("yukari_like", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/y_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/y_like");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/y_rt");
        }});
        SE_URIS.put("yukari_love", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/y_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/y_love");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/y_rt");
        }});
        SE_URIS.put("akari_fav", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/akari_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/akari_fav");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/akari_retweet");
        }});
        SE_URIS.put("akari_like", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/akari_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/akari_like");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/akari_retweet");
        }});
        SE_URIS.put("akari_love", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/akari_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/akari_love");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/akari_retweet");
        }});
        SE_URIS.put("kiri_like", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/kiri_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/kiri_like");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/kiri_retweet");
        }});
        SE_URIS.put("kiri_love", new SoundEffects() {{
            reply = Uri.parse("android.resource://shibafu.yukari/raw/kiri_reply");
            favorite = Uri.parse("android.resource://shibafu.yukari/raw/kiri_suki");
            retweet = Uri.parse("android.resource://shibafu.yukari/raw/kiri_retweet");
        }});
    }

    public StatusNotifier(TwitterService service) {
        this.service = service;
        this.context = service.getApplicationContext();

        this.handler = new Handler();

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (new File(context.getExternalFilesDir(null), USER_SE_REPLY).exists()) {
            Log.d(LOG_TAG, "User reply notify sound detected.");
            useUserFileOnReply = true;
        }
        if (new File(context.getExternalFilesDir(null), USER_SE_RETWEET).exists()) {
            Log.d(LOG_TAG, "User retweet notify sound detected.");
            useUserFileOnRetweet = true;
        }
        if (new File(context.getExternalFilesDir(null), USER_SE_FAVORITE).exists()) {
            Log.d(LOG_TAG, "User favorite notify sound detected.");
            useUserFileOnFavorite = true;
        }
    }

    private Uri getNotificationUrl(int category) {
        boolean useYukariVoice = sharedPreferences.getBoolean("j_yukari_voice", false);
        switch (category) {
            case R.integer.notification_replied:
            case R.integer.notification_message:
            case R.integer.notification_respond:
                if (useUserFileOnReply) {
                    return Uri.fromFile(new File(context.getExternalFilesDir(null), USER_SE_REPLY));
                } else if (useYukariVoice) {
                    return SE_URIS.get(sharedPreferences.getString("pref_sound_theme", "yukari_fav")).reply;
                } else {
                    return SE_URIS.get("default").reply;
                }
            case R.integer.notification_retweeted:
                if (useUserFileOnRetweet) {
                    return Uri.fromFile(new File(context.getExternalFilesDir(null), USER_SE_RETWEET));
                } else if (useYukariVoice) {
                    return SE_URIS.get(sharedPreferences.getString("pref_sound_theme", "yukari_fav")).retweet;
                } else {
                    return SE_URIS.get("default").retweet;
                }
            case R.integer.notification_faved:
                if (useUserFileOnFavorite) {
                    return Uri.fromFile(new File(context.getExternalFilesDir(null), USER_SE_FAVORITE));
                } else if (useYukariVoice) {
                    return SE_URIS.get(sharedPreferences.getString("pref_sound_theme", "yukari_fav")).favorite;
                } else {
                    return SE_URIS.get("default").favorite;
                }
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
                    titleHeader = "Retweeted by @";
                    tickerHeader = "RTされました : @";
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
                    titleHeader = "RT-Respond from @";
                    tickerHeader = "RTレスポンス : @";
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
                builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                builder.setContentIntent(CompatUtil.getEmptyPendingIntent(context));
                builder.setTicker(tickerHeader + actionBy.getScreenName());
                builder.setColor(color);
                if (notificationType.isUseSound()) {
                    builder.setSound(sound, AudioManager.STREAM_NOTIFICATION);
                }
                if (notificationType.isUseVibration()) {
                    builder.setVibrate(pattern);
                }
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                builder.setAutoCancel(true);
                switch (category) {
                    case R.integer.notification_replied:
                    {
                        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_MENTION);
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                context.getApplicationContext(), R.integer.notification_replied, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentIntent(pendingIntent);

                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            replyIntent.putExtra(TweetActivity.EXTRA_STATUS, status.getOriginStatus());
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            replyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                    status.getOriginStatus().getUser().getScreenName() + " ");
                            builder.addAction(R.drawable.ic_stat_reply, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_replied, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
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
                                            PendingIntent.FLAG_UPDATE_CURRENT))
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
                                context.getApplicationContext(), R.integer.notification_respond, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentIntent(pendingIntent);
                        break;
                    }
                    case R.integer.notification_message:
                    {
                        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SHOW_TAB, TabType.TABTYPE_DM);
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                context.getApplicationContext(), R.integer.notification_message, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentIntent(pendingIntent);

                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            replyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, status.getUser().getId());
                            replyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, status.getUser().getScreenName());
                            builder.addAction(R.drawable.ic_stat_message, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_message, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            );
                        }
                        {
                            Intent voiceReplyIntent = new Intent(context.getApplicationContext(), PostService.class);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_USER, status.getRepresentUser());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, status.getUser().getId());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, status.getUser().getScreenName());
                            NotificationCompat.Action voiceReply = new NotificationCompat.Action
                                    .Builder(R.drawable.ic_stat_reply, "声で返信",
                                    PendingIntent.getService(context.getApplicationContext(),
                                            R.integer.notification_replied,
                                            voiceReplyIntent,
                                            PendingIntent.FLAG_UPDATE_CURRENT))
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
                if (notificationType.isUseSound() && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                    MediaPlayer mediaPlayer = MediaPlayer.create(context, sound);
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
                    mediaPlayer.start();
                }
                if (notificationType.isUseVibration()) {
                    switch (audioManager.getRingerMode()) {
                        case AudioManager.RINGER_MODE_NORMAL:
                        case AudioManager.RINGER_MODE_VIBRATE:
                            vibrator.vibrate(pattern, -1);
                            break;
                    }
                }
                final String text = tickerHeader + actionBy.getScreenName() + "\n" +
                        status.getUser().getScreenName() + ": " + status.getText();
                handler.post(() -> Toast.makeText(context.getApplicationContext(),
                        text,
                        Toast.LENGTH_LONG)
                        .show());
            }
        }
    }

    @Override
    public void release() {
        AutoReleaser.release(this);
    }
    
    private static class SoundEffects {
        public Uri reply;
        public Uri favorite;
        public Uri retweet;
    }
}
