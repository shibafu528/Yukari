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
import android.util.Log;
import shibafu.yukari.R;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.entity.InReplyToId;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.linkage.ProviderApiException;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.CompatUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id_async_action))
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

        //添付メディアのアップロード準備
        List<File> uploadMediaList;
        try {
            uploadMediaList = prepareUploadMedia(draft.getAttachPictures(), imageResizeLength);
        } catch (IOException e) {
            nm.cancel(0);
            showErrorMessage(1, draft, "添付画像の処理エラー");
            return;
        }

        //順次投稿
        int error = 0;
        try {
            for (int i = 0; i < writers.size(); ++i) {
                AuthUserRecord userRecord = writers.get(i);
                try {
                    ProviderApi api = service.getProviderApi(userRecord);
                    if (api == null) {
                        throw new RuntimeException("Critical Error : ProviderAPI not found.");
                    }

                    // 事前処理Flagがある場合はそれらを実行する
                    if (targetStatus != null) {
                        if ((flags & FLAG_FAVORITE) == FLAG_FAVORITE) {
                            api.createFavorite(userRecord, targetStatus);
                        }
                        if ((flags & FLAG_RETWEET) == FLAG_RETWEET) {
                            api.repostStatus(userRecord, targetStatus);
                        }
                    }

                    // 投稿
                    api.postStatus(userRecord, draft, uploadMediaList);
                } catch (ProviderApiException e) {
                    e.printStackTrace();
                    showErrorMessage(i + 65535, draft, String.format(
                            "@%s/%s",
                            userRecord.ScreenName,
                            e.getMessage()));
                    ++error;
                }
                builder.setWhen(System.currentTimeMillis());
                builder.setContentInfo((i + 1) + "/" + writers.size());
                builder.setProgress(writers.size(), i + 1, false);
                nm.notify(R.integer.notification_tweet, builder.build());
            }
        } finally {
            //添付メディアの一時ファイルを削除
            for (File file : uploadMediaList) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
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

    private List<File> prepareUploadMedia(List<Uri> mediaList, int imageResizeLength) throws IOException {
        List<File> uploadMedia = new ArrayList<>();
        try {
            for (Uri uri : mediaList) {
                InputStream is = null;
                try {
                    //リサイズテスト
                    int[] size = new int[2];
                    try {
                        Bitmap thumb = BitmapUtil.resizeBitmap(this, uri, 128, 128, size);
                        thumb.recycle();
                    } catch (IOException | NullPointerException e) {
                        throw new IOException(e);
                    }
                    //リサイズする必要があるか？
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
                    //一時ファイルにコピー
                    File tempFile = File.createTempFile("uploadMedia", ".tmp", getExternalCacheDir());
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[4096];
                    int length;
                    try {
                        while ((length = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, length);
                        }
                    } finally {
                        try {
                            fos.close();
                        } catch (IOException ignored) {}
                    }
                    uploadMedia.add(tempFile);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {}
                    }
                }
            }
            return uploadMedia;
        } catch (IOException e) {
            for (File file : uploadMedia) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            throw e;
        }
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
                draft.setInReplyTo(new InReplyToId(String.valueOf(inReplyToId)));
                draft.setDirectMessage(true);
                draft.setText(String.valueOf(voiceInput));
            } else {
                Status inReplyToStatus = (Status) intent.getSerializableExtra(TweetActivity.EXTRA_STATUS);

                draft.setInReplyTo(inReplyToStatus.getInReplyTo());
                draft.setText(prefix + voiceInput);
            }

            return draft;
        }
        return null;
    }

    private void showErrorMessage(int id, StatusDraft draft, String reason) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id_error))
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
