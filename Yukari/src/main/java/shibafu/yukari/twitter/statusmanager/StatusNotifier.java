package shibafu.yukari.twitter.statusmanager;

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
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.TabType;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.util.AutoRelease;
import shibafu.yukari.util.CompatUtil;
import shibafu.yukari.util.Releasable;
import twitter4j.DirectMessage;
import twitter4j.TwitterResponse;
import twitter4j.User;

/**
 * Created by shibafu on 2015/07/27.
 */
class StatusNotifier implements Releasable {

    //バイブレーションパターン
    private final static long[] VIB_REPLY = {450, 130, 140, 150};
    private final static long[] VIB_RETWEET = {150, 130, 300, 150};
    private final static long[] VIB_FAVED = {140, 100};

    @AutoRelease
    private StatusManager parent;
    @AutoRelease private Context context;
    @AutoRelease private Handler handler;
    @AutoRelease private SharedPreferences sharedPreferences;
    @AutoRelease private NotificationManager notificationManager;
    @AutoRelease private AudioManager audioManager;
    @AutoRelease private Vibrator vibrator;

    public StatusNotifier(Context context, StatusManager parent) {
        this.parent = parent;
        this.context = context;

        this.handler = new Handler();

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private Uri getNotificationUrl(int category) {
        boolean useYukariVoice = sharedPreferences.getBoolean("j_yukari_voice", false);
        switch (category) {
            case R.integer.notification_replied:
            case R.integer.notification_message:
            case R.integer.notification_respond:
                if (useYukariVoice) {
                    return Uri.parse("android.resource://shibafu.yukari/raw/y_reply");
                } else {
                    return Uri.parse("android.resource://shibafu.yukari/raw/se_reply");
                }
            case R.integer.notification_retweeted:
                if (useYukariVoice) {
                    return Uri.parse("android.resource://shibafu.yukari/raw/y_rt");
                } else {
                    return Uri.parse("android.resource://shibafu.yukari/raw/se_rt");
                }
            case R.integer.notification_faved:
                if (useYukariVoice) {
                    return Uri.parse("android.resource://shibafu.yukari/raw/y_fav");
                } else {
                    return Uri.parse("android.resource://shibafu.yukari/raw/se_fav");
                }
            default:
                return null;
        }
    }

    public void showNotification(int category, TwitterResponse status, User actionBy) {
        TweetCommonDelegate delegate = TweetCommon.newInstance(status.getClass());

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
            int icon = 0, color = context.getResources().getColor(R.color.key_color);
            Uri sound = getNotificationUrl(category);
            String titleHeader = "", tickerHeader = "";
            long[] pattern = null;
            switch (category) {
                case R.integer.notification_replied:
                    icon = R.drawable.ic_stat_reply;
                    titleHeader = "Reply from @";
                    tickerHeader = "リプライ : @";
                    pattern = VIB_REPLY;
                    break;
                case R.integer.notification_retweeted:
                    icon = R.drawable.ic_stat_retweet;
                    titleHeader = "Retweeted by @";
                    tickerHeader = "RTされました : @";
                    pattern = VIB_RETWEET;
                    color = Color.rgb(0, 128, 0);
                    break;
                case R.integer.notification_faved:
                    icon = R.drawable.ic_stat_favorite;
                    titleHeader = "Faved by @";
                    tickerHeader = "ふぁぼられ : @";
                    pattern = VIB_FAVED;
                    color = Color.rgb(255, 128, 0);
                    break;
                case R.integer.notification_message:
                    icon = R.drawable.ic_stat_message;
                    titleHeader = "Message from @";
                    tickerHeader = "DM : @";
                    pattern = VIB_REPLY;
                    break;
                case R.integer.notification_respond:
                    icon = R.drawable.ic_stat_reply;
                    titleHeader = "RT-Respond from @";
                    tickerHeader = "RTレスポンス : @";
                    pattern = VIB_REPLY;
                    break;
            }
            if (notificationType.getNotificationType() == NotificationType.TYPE_NOTIF) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());
                builder.setSmallIcon(icon);
                builder.setContentTitle(titleHeader + actionBy.getScreenName());
                builder.setContentText(delegate.getUser(status).getScreenName() + ": " + delegate.getText(status));
                builder.setContentIntent(CompatUtil.getEmptyPendingIntent(context));
                builder.setTicker(tickerHeader + actionBy.getScreenName());
                builder.setColor(color);
                if (notificationType.isUseSound()) {
                    builder.setSound(sound, AudioManager.STREAM_NOTIFICATION);
                }
                if (notificationType.isUseVibration()) {
                    vibrate(pattern, -1);
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

                        PreformedStatus ps = (PreformedStatus) status;
                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, ps.getRepresentUser());
                            replyIntent.putExtra(TweetActivity.EXTRA_STATUS, ((ps.isRetweet()) ? ps.getRetweetedStatus() : ps));
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            replyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                    ((ps.isRetweet()) ? ps.getRetweetedStatus().getUser().getScreenName()
                                            : ps.getUser().getScreenName()) + " ");
                            builder.addAction(R.drawable.ic_stat_reply, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_replied, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            );
                        }
                        {
                            Intent voiceReplyIntent = new Intent(context.getApplicationContext(), PostService.class);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_USER, ps.getRepresentUser());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_STATUS, ((ps.isRetweet()) ? ps.getRetweetedStatus() : ps));
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                    ((ps.isRetweet()) ? ps.getRetweetedStatus().getUser().getScreenName()
                                            : ps.getUser().getScreenName()) + " ");
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

                        DirectMessage dm = (DirectMessage) status;
                        {
                            Intent replyIntent = new Intent(context.getApplicationContext(), TweetActivity.class);
                            replyIntent.putExtra(TweetActivity.EXTRA_USER, parent.findUserRecord(dm.getRecipient()));
                            replyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            replyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, dm.getSenderId());
                            replyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, dm.getSenderScreenName());
                            builder.addAction(R.drawable.ic_stat_message, "返信", PendingIntent.getActivity(
                                            context.getApplicationContext(), R.integer.notification_message, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                            );
                        }
                        {
                            Intent voiceReplyIntent = new Intent(context.getApplicationContext(), PostService.class);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_USER, parent.findUserRecord(dm.getRecipient()));
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, dm.getSenderId());
                            voiceReplyIntent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, dm.getSenderScreenName());
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
                if (notificationType.isUseSound()) {
                    MediaPlayer mediaPlayer = MediaPlayer.create(context, sound);
                    mediaPlayer.start();
                }
                if (notificationType.isUseVibration()) {
                    vibrate(pattern, -1);
                }
                final String text = tickerHeader + actionBy.getScreenName() + "\n" +
                        delegate.getUser(status).getScreenName() + ": " + delegate.getText(status);
                handler.post(() -> Toast.makeText(context.getApplicationContext(),
                        text,
                        Toast.LENGTH_LONG)
                        .show());
            }
        }
    }

    // サイレントに設定しててもうっかりバイブが震えちゃうような
    // クソ端末でそのような挙動が起きないように
    private void vibrate(long[] pattern, int repeat) {
        switch (audioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
            case AudioManager.RINGER_MODE_VIBRATE:
                vibrator.vibrate(pattern, repeat);
                break;
        }
    }
}
