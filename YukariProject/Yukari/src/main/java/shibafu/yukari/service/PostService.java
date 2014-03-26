package shibafu.yukari.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.BitmapResizer;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/03/26.
 */
public class PostService extends IntentService{
    public static final String EXTRA_DRAFT = "draft";

    private NotificationManager nm;
    private Bitmap icon;

    private TwitterService service;
    private boolean serviceBound;

    public PostService() {
        super("PostService");
    }

    public static Intent newIntent(Context context, TweetDraft draft) {
        Intent intent = new Intent(context, PostService.class);
        intent.putExtra(EXTRA_DRAFT, draft);
        return intent;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        TweetDraft draft = (TweetDraft) intent.getSerializableExtra(EXTRA_DRAFT);
        if (draft == null) {
            nm.cancel(0);
            showErrorMessage(1, null, "データが破損しています");
            return;
        }
        ArrayList<AuthUserRecord> writers = draft.getWriters();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setTicker("ツイートを送信中")
                .setContentTitle("ツイートを送信中")
                .setContentText(draft.getText())
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(1, 0, true)
                .setOngoing(true);
        if (writers.size() > 1) {
            builder.setContentInfo("1/" + writers.size());
            builder.setProgress(writers.size(), 0, false);
        }
        else {
            icon = BitmapCache.getImage(writers.get(0).ProfileImageUrl, this, BitmapCache.PROFILE_ICON_CACHE);
            if (icon != null) {
                builder.setLargeIcon(icon);
            }
        }
        nm.notify(0, builder.build());

        //サービスバインドまで待機
        while (!serviceBound) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //順次投稿
        StatusUpdate update = new StatusUpdate(draft.getText());
        int error = 0;
        for (int i = 0; i < writers.size(); ++i) {
            AuthUserRecord userRecord = writers.get(i);
            try {
                if (draft.isDirectMessage()) {
                    service.sendDirectMessage(draft.getMessageTarget(), userRecord, draft.getText());
                } else {
                    //statusが引数に付加されている場合はin-reply-toとして設定する
                    if (draft.getInReplyTo() > -1) {
                        update.setInReplyToStatusId(draft.getInReplyTo());
                    }
                    //attachPictureがある場合は添付
                    if (draft.getAttachedPicture() != null) {
                        int[] size = new int[2];
                        {
                            Bitmap thumb = BitmapResizer.resizeBitmap(this, draft.getAttachedPicture(), 128, 128, size);
                            thumb.recycle();
                        }
                        if (Math.max(size[0], size[1]) > 960) {
                            Log.d("TweetActivity", "添付画像の長辺が960pxを超えています。圧縮対象とします。");
                            Bitmap resized = BitmapResizer.resizeBitmap(this, draft.getAttachedPicture(), 960, 960, null);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            resized.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                            Log.d("TweetActivity", "縮小しました w=" + resized.getWidth() + " h=" + resized.getHeight());
                            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                            service.postTweet(userRecord, update, new InputStream[]{bais});
                        } else {
                            service.postTweet(
                                    userRecord,
                                    update,
                                    new InputStream[]{
                                            getContentResolver().openInputStream(draft.getAttachedPicture())
                                    }
                            );
                        }
                    } else {
                        service.postTweet(userRecord, update);
                    }
                }
            } catch (TwitterException e) {
                e.printStackTrace();
                showErrorMessage(i + 1, draft, String.format(
                        "@%s/%d %s",
                        userRecord.ScreenName,
                        e.getErrorCode(),
                        e.getErrorMessage()));
                ++error;
            } catch (IOException e) {
                e.printStackTrace();
                showErrorMessage(i + 1, draft, String.format(
                        "@%s/添付画像のデコードエラー",
                        userRecord.ScreenName));
                ++error;
            }
            builder.setWhen(System.currentTimeMillis());
            builder.setContentInfo((i + 1) + "/" + writers.size());
            builder.setProgress(writers.size(), i + 1, false);
            nm.notify(0, builder.build());
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
            nm.notify(0, builder.build());
        }
        else if (error == 0) {
            builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);
            builder.setTicker("ツイートに成功しました");
            builder.setContentTitle("ツイートに成功しました");
            builder.setContentText("");
            nm.notify(0, builder.build());
        }
        nm.cancel(0);
    }

    private void showErrorMessage(int id, TweetDraft draft, String reason) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setTicker("ツイートに失敗しました")
                .setContentTitle("ツイートに失敗しました")
                .setContentText(reason)
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
