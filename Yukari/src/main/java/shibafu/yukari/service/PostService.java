package shibafu.yukari.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.text.TextUtils;
import android.util.Log;
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException;
import shibafu.yukari.R;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.database.Provider;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.CompatUtil;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shibafu on 14/03/26.
 */
public class PostService extends IntentService{
    public static final String EXTRA_DRAFT = "draft";
    public static final String EXTRA_FLAGS = "flags";
    public static final String EXTRA_FLAGS_TARGET_STATUS = "targetStatus";
    public static final String EXTRA_REMOTE_INPUT = "remoteInput";
    public static final int FLAG_FAVORITE  = 0x01;
    public static final int FLAG_RETWEET   = 0x02;

    private static final Pattern PATTERN_QUOTE = Pattern.compile(" https?://(mobile\\.|www\\.)?twitter\\.com/[0-9a-zA-Z_]{1,15}/status(es)?/\\d+/?$");

    private NotificationManager nm;
    private Bitmap icon;

    private TwitterService service;
    private boolean serviceBound;

    public PostService() {
        super("PostService");
    }

    public static Intent newIntent(Context context, StatusDraft draft) {
        Intent intent = new Intent(context, PostService.class);
        intent.putExtra(EXTRA_DRAFT, draft);
        return intent;
    }

    public static Intent newIntent(Context context, StatusDraft draft, int flags, Status targetStatus) {
        Intent intent = new Intent(context, PostService.class);
        intent.putExtra(EXTRA_DRAFT, draft);
        intent.putExtra(EXTRA_FLAGS, flags);
        intent.putExtra(EXTRA_FLAGS_TARGET_STATUS, targetStatus);
        return intent;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int imageResizeLength = Integer.valueOf(sp.getString("pref_upload_size", "960"));
        Log.d("PostService", "Upload Limit : " + imageResizeLength + "px");

        int flags = intent.getIntExtra(EXTRA_FLAGS, 0);
        Status targetStatus = (Status) intent.getSerializableExtra(EXTRA_FLAGS_TARGET_STATUS);
        StatusDraft draft = intent.getParcelableExtra(EXTRA_DRAFT);
        if (RemoteInput.getResultsFromIntent(intent) != null) {
            draft = parseRemoteInput(intent);
        }
        if (draft == null) {
            nm.cancel(0);
            showErrorMessage(1, null, "データが破損しています");
            return;
        }
        ArrayList<AuthUserRecord> writers = draft.getWriters();
        if (writers == null || writers.isEmpty()) {
            nm.cancel(0);
            showErrorMessage(1, null, "投稿アカウントが指定されていません");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setTicker("ツイートを送信中")
                .setContentTitle("ツイートを送信中")
                .setContentText(draft.getText())
                .setContentIntent(CompatUtil.getEmptyPendingIntent(getApplicationContext()))
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(1, 0, true)
                .setOngoing(true);
        if (writers.size() > 1) {
            builder.setContentInfo("1/" + writers.size());
            builder.setProgress(writers.size(), 0, false);
        }
        else {
            icon = BitmapCache.getImage(writers.get(0).ProfileImageUrl, BitmapCache.PROFILE_ICON_CACHE, this);
            if (icon != null) {
                builder.setLargeIcon(icon);
            }
        }
        startForeground(R.integer.notification_tweet, builder.build());

        //サービスバインドまで待機
        while (!serviceBound) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //投稿用データの準備
        StatusUpdate update;
        {
            String text = draft.getText();
            String attachmentUrl = null;

            //画像添付なしで、ツイートを引用している場合は1つだけattachmentUrlに移動させる
            if (draft.getAttachPictures().isEmpty()) {
                Matcher attachmentMatcher = PATTERN_QUOTE.matcher(draft.getText());
                if (attachmentMatcher.find()) {
                    attachmentUrl = attachmentMatcher.group().trim();
                    text = attachmentMatcher.replaceAll("");
                }
            }

            update = new StatusUpdate(text);
            if (attachmentUrl != null) {
                update.setAttachmentUrl(attachmentUrl);
            }
        }

        //順次投稿
        int error = 0;
        for (int i = 0; i < writers.size(); ++i) {
            AuthUserRecord userRecord = writers.get(i);
            try {
                switch (userRecord.Provider.getApiType()) {
                    case Provider.API_TWITTER:
                        if (draft.isDirectMessage()) {
                            service.sendDirectMessage(draft.getMessageTarget(), userRecord, draft.getText());
                        } else {
                            //事前処理Flagがある場合はそれらを実行する
                            if (targetStatus != null) {
                                if ((flags & FLAG_FAVORITE) == FLAG_FAVORITE) {
                                    service.getProviderApi(userRecord).createFavorite(userRecord, targetStatus);
                                }
                                if ((flags & FLAG_RETWEET) == FLAG_RETWEET) {
                                    service.getProviderApi(userRecord).repostStatus(userRecord, targetStatus);
                                }
                            }
                            //statusが引数に付加されている場合はin-reply-toとして設定する
                            if (!TextUtils.isEmpty(draft.getInReplyTo())) {
                                long inReplyTo = TwitterUtil.getStatusIdFromUrl(draft.getInReplyTo());
                                if (inReplyTo > -1) {
                                    update.setInReplyToStatusId(inReplyTo);
                                }
                            }
                            //attachPictureがある場合は添付
                            if (!draft.getAttachPictures().isEmpty()) {
                                List<Uri> attachedPictures = draft.getAttachPictures();
                                long[] mediaIds = new long[attachedPictures.size()];
                                for (int j = 0; j < mediaIds.length; ++j) {
                                    Uri u = attachedPictures.get(j);
                                    InputStream is;
                                    int[] size = new int[2];
                                    try {
                                        Bitmap thumb = BitmapUtil.resizeBitmap(this, u, 128, 128, size);
                                        thumb.recycle();
                                    } catch (IOException | NullPointerException e) {
                                        throw new IOException(e);
                                    }
                                    if (imageResizeLength > 0 && Math.max(size[0], size[1]) > imageResizeLength) {
                                        Log.d("PostService", "添付画像の長辺が設定値を超えています。圧縮対象とします。");
                                        Bitmap resized = BitmapUtil.resizeBitmap(
                                                this,
                                                u,
                                                imageResizeLength,
                                                imageResizeLength,
                                                null);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        resized.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                                        Log.d("PostService", "縮小しました w=" + resized.getWidth() + " h=" + resized.getHeight());
                                        is = new ByteArrayInputStream(baos.toByteArray());
                                    } else {
                                        is = getContentResolver().openInputStream(u);
                                    }

                                    mediaIds[j] = service.uploadMedia(userRecord, is).getMediaId();
                                }
                                update.setMediaIds(mediaIds);
                            }
                            service.postTweet(userRecord, update);
                        }
                        break;
                    case Provider.API_MASTODON: {
                        //事前処理Flagがある場合はそれらを実行する
                        if (targetStatus != null) {
                            if ((flags & FLAG_FAVORITE) == FLAG_FAVORITE) {
                                service.getProviderApi(userRecord).createFavorite(userRecord, targetStatus);
                            }
                            if ((flags & FLAG_RETWEET) == FLAG_RETWEET) {
                                service.getProviderApi(userRecord).repostStatus(userRecord, targetStatus);
                            }
                        }
                        //attachPictureがある場合は添付
                        List<Long> mediaIds = null;
                        if (!draft.getAttachPictures().isEmpty()) {
                            mediaIds = new ArrayList<>();
                            for (Uri uri : draft.getAttachPictures()) {
                                InputStream is;
                                int[] size = new int[2];
                                try {
                                    Bitmap thumb = BitmapUtil.resizeBitmap(this, uri, 128, 128, size);
                                    thumb.recycle();
                                } catch (IOException | NullPointerException e) {
                                    throw new IOException(e);
                                }
                                if (imageResizeLength > 0 && Math.max(size[0], size[1]) > imageResizeLength) {
                                    Log.d("PostService", "添付画像の長辺が設定値を超えています。圧縮対象とします。");
                                    Bitmap resized = BitmapUtil.resizeBitmap(
                                            this,
                                            uri,
                                            imageResizeLength,
                                            imageResizeLength,
                                            null);
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    resized.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                                    Log.d("PostService", "縮小しました w=" + resized.getWidth() + " h=" + resized.getHeight());
                                    is = new ByteArrayInputStream(baos.toByteArray());
                                } else {
                                    is = getContentResolver().openInputStream(uri);
                                }

                                mediaIds.add(service.uploadMediaToMastodon(userRecord, is).getId());
                            }
                        }
                        service.postToot(userRecord, draft.getText(), mediaIds);
                        break;
                    }
                }
            } catch (TwitterException e) {
                e.printStackTrace();
                showErrorMessage(i + 65535, draft, String.format(
                        "@%s/%d %s",
                        userRecord.ScreenName,
                        e.getErrorCode(),
                        e.getErrorMessage()));
                ++error;
            } catch (Mastodon4jRequestException e) {
                e.printStackTrace();
                try {
                    showErrorMessage(i + 65535, draft, String.format(
                            "@%s/%d %s",
                            userRecord.ScreenName,
                            e.getResponse().code(),
                            e.getResponse().body().string()));
                } catch (IOException e1) {
                    e1.printStackTrace();
                    showErrorMessage(i + 65535, draft, String.format(
                            "@%s/%d %s",
                            userRecord.ScreenName,
                            e.getResponse().code(),
                            "Unknown error"));
                }
                ++error;
            } catch (IOException e) {
                e.printStackTrace();
                showErrorMessage(i + 65535, draft, String.format(
                        "@%s/添付画像の処理エラー",
                        userRecord.ScreenName));
                ++error;
            }
            builder.setWhen(System.currentTimeMillis());
            builder.setContentInfo((i + 1) + "/" + writers.size());
            builder.setProgress(writers.size(), i + 1, false);
            nm.notify(R.integer.notification_tweet, builder.build());
        }

        builder.setAutoCancel(true);
        builder.setWhen(System.currentTimeMillis());
        builder.setProgress(0, 0, false);
        builder.setOngoing(false);
        if (error > 0 && error < writers.size()) {
            builder.setSmallIcon(android.R.drawable.stat_notify_error);
            builder.setTicker("一部ツイートに失敗しました");
            builder.setContentTitle("一部ツイートに失敗しました");
            builder.setContentText("一部アカウントで投稿中にエラーが発生しました");
            nm.notify(R.integer.notification_tweet, builder.build());
        }
        else if (error == 0) {
            builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);
            builder.setTicker("ツイートに成功しました");
            builder.setContentTitle("ツイートに成功しました");
            builder.setContentText("");
            nm.notify(R.integer.notification_tweet, builder.build());
            service.getDatabase().deleteDraft(draft);
        }

        //ゆかりさんが反応する機能
        if (sp.getBoolean("j_yukari_voice", false) && sp.getBoolean("j_yukari_joke_voices", false)) {
            reactionFromYukari(draft);
        }

        stopForeground(true);
    }

    private void reactionFromYukari(StatusDraft draft) {
        if (draft.getText().contains("壁")) {
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.y_wall);
            mp.start();
        } else if (draft.getText().contains("床")) {
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.y_floor);
            mp.start();
        } else if (draft.getText().contains("まないた") || draft.getText().contains("まな板") || draft.getText().contains("洗濯板")) {
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.y_tweet_notice);
            mp.start();
        }
    }

    private StatusDraft parseRemoteInput(Intent intent) {
        //Wearからの音声入力を受け取る
        Bundle input = RemoteInput.getResultsFromIntent(intent);
        if (input != null) {
            AuthUserRecord user = (AuthUserRecord) intent.getSerializableExtra(TweetActivity.EXTRA_USER);
            int mode = intent.getIntExtra(TweetActivity.EXTRA_MODE, 0);
            String prefix = intent.getStringExtra(TweetActivity.EXTRA_TEXT);
            CharSequence voiceInput = input.getCharSequence(EXTRA_REMOTE_INPUT);

            StatusDraft draft = new StatusDraft();
            draft.setWriters(user.toSingleList());

            if (mode == TweetActivity.MODE_DM) {
                String targetSN = intent.getStringExtra(TweetActivity.EXTRA_DM_TARGET_SN);
                long inReplyToId = intent.getLongExtra(TweetActivity.EXTRA_IN_REPLY_TO, -1);

                draft.setMessageTarget(targetSN);
                draft.setInReplyTo(String.valueOf(inReplyToId));
                draft.setDirectMessage(true);
                draft.setText(String.valueOf(voiceInput));
            } else {
                Status inReplyToStatus = (Status) intent.getSerializableExtra(TweetActivity.EXTRA_STATUS);

                draft.setInReplyTo(inReplyToStatus.getUrl());
                draft.setText(prefix + voiceInput);
            }

            return draft;
        }
        return null;
    }

    private void showErrorMessage(int id, StatusDraft draft, String reason) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setTicker("ツイートに失敗しました")
                .setContentTitle("ツイートに失敗しました")
                .setContentText(reason)
                .setContentIntent(CompatUtil.getEmptyPendingIntent(getApplicationContext()))
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setWhen(System.currentTimeMillis());
        if (icon != null) {
            builder.setLargeIcon(icon);
        }
        if (draft != null) {
            Intent intent = draft.getTweetIntent(this);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
            builder.setContentIntent(pendingIntent);
            //下書きを保存
            draft.setFailedDelivery(true);
            service.getDatabase().updateDraft(draft);
        }
        nm.notify(id, builder.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("PostService", "onCreate PostService");
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("PostService", "onDestory PostService");
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("PostService", "onServiceConnected");
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            PostService.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };
}
