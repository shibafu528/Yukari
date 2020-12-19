package shibafu.yukari.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;
import permissions.dispatcher.RuntimePermissions;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.entity.Status;
import shibafu.yukari.media2.Media;
import shibafu.yukari.media2.MediaFactory;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.StringUtil;
import shibafu.yukari.view.StatusView;
import shibafu.yukari.view.TweetView;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Created by Shibafu on 13/09/22.
 */
@RuntimePermissions
public class PreviewActivity extends ActionBarYukariBase {
    //TODO: DMの添付画像をダウンロードできるようにする
    //OAuthが必要なためにOSのダウンロードキューに直接ぶち込むことが出来ない
    //アプリ独自で保存処理を行う必要がある
    //キャッシュに既にいるのでそいつを使うという手はある

    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_USER = "user";

    private Media media;
    private Bitmap image;
    private Matrix matrix;
    private float minScale = 1.0f;

    private ParallelAsyncTask<String, Object, Bitmap> loaderTask = null;

    @BindView(R.id.ivPreviewImage) ImageView imageView;
    @BindView(R.id.twvPreviewStatus) TweetView tweetView;
    private Status status;
    private AuthUserRecord user;

    private Animation animFadeIn, animFadeOut;
    private boolean isShowPanel = true;
    private int displayWidth;
    private int displayHeight;

    @BindView(R.id.ibPreviewSave) ImageButton ibSave;

    @BindView(R.id.tvPreviewProgress) TextView loadProgressText;
    @BindView(R.id.tvPreviewProgress2) TextView loadProgressText2;

    @BindView(R.id.llQrText) LinearLayout llQrText;
    @BindView(R.id.tvQrText) TextView tvQrText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        setContentView(R.layout.activity_imagepreview);

        final Uri data = getIntent().getData();
        if (data == null) {
            Toast.makeText(this, "null uri", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        else if ("vine.co".equals(data.getHost()) || data.toString().contains("pbs.twimg.com/tweet_video/") || "video.twimg.com".equals(data.getHost())) {
            Intent intent = new Intent(Intent.ACTION_VIEW, data, this, MoviePreviewActivity.class);
            intent.putExtras(getIntent());
            startActivity(intent);
            finish();
            return;
        }

        user = (AuthUserRecord) getIntent().getSerializableExtra(EXTRA_USER);

        ButterKnife.bind(this);

        animFadeIn = AnimationUtils.loadAnimation(this, R.anim.anim_fadein);
        animFadeOut = AnimationUtils.loadAnimation(this, R.anim.anim_fadeout);

        final LinearLayout llControlPanel = (LinearLayout) findViewById(R.id.llPreviewPanel);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private static final int TOUCH_NONE = 0;
            private static final int TOUCH_DRAG = 1;
            private static final int TOUCH_ZOOM = 2;
            private int touchMode = TOUCH_NONE;

            private static final int DRAG_THRESHOLD = 30;
            private float dragStartX, dragStartY, dragPrevX, dragPrevY;
            private boolean begunDrag = false;

            private double pinchPrevDistance;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (image == null) return false;
                //ピンチの処理
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() >= 2) {
                            pinchPrevDistance = getDistance(event);
                            if (pinchPrevDistance > 50f) {
                                touchMode = TOUCH_ZOOM;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (touchMode == TOUCH_ZOOM && event.getPointerCount() >= 2) {
                            if (isShowPanel) {
                                llControlPanel.startAnimation(animFadeOut);
                                llControlPanel.setVisibility(View.INVISIBLE);
                                isShowPanel = false;
                            }

                            double distance = getDistance(event);
                            double scale = (distance - pinchPrevDistance) / Math.sqrt(displayWidth * displayWidth + displayHeight * displayHeight);
                            pinchPrevDistance = distance;
                            scale += 1;
                            scale = scale * scale;

                            float[] matrixValues = new float[9];
                            matrix.getValues(matrixValues);
                            float nowScale = matrixValues[Matrix.MSCALE_X];
                            if (nowScale * scale > minScale) {
                                matrix.postScale((float) scale, (float) scale);
                                matrix.postTranslate((float) -(displayWidth * scale - displayWidth) / 2,
                                        (float) -(displayHeight * scale - displayHeight) / 2);
                                matrix.postTranslate((float) (-(displayWidth/2 - displayWidth/2) * scale), 0);
                                matrix.postTranslate(0, (float) (-(displayHeight/2 - displayHeight/2) * scale));
                                updateMatrix();
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (touchMode == TOUCH_ZOOM) {
                            touchMode = TOUCH_NONE;
                        }
                        break;
                }

                //ドラッグの処理
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (touchMode == TOUCH_NONE && event.getPointerCount() == 1) {
                            touchMode = TOUCH_DRAG;
                            dragPrevX = dragStartX = event.getX();
                            dragPrevY = dragStartY = event.getY();
                            begunDrag = false;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (touchMode == TOUCH_DRAG) {
                            int moveX = (int) (event.getX() - dragStartX);
                            int moveY = (int) (event.getY() - dragStartY);
                            if (begunDrag || Math.max(Math.abs(moveX), Math.abs(moveY)) > DRAG_THRESHOLD) {
                                if (isShowPanel) {
                                    llControlPanel.startAnimation(animFadeOut);
                                    llControlPanel.setVisibility(View.INVISIBLE);
                                    isShowPanel = false;
                                }
                                matrix.postTranslate(-(dragPrevX - event.getX()), -(dragPrevY - event.getY()));
                                updateMatrix();
                                begunDrag = true;
                            }
                            dragPrevX = event.getX();
                            dragPrevY = event.getY();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (touchMode == TOUCH_DRAG) {
                            touchMode = TOUCH_NONE;
                            if (!begunDrag) {
                                if (isShowPanel) {
                                    llControlPanel.startAnimation(animFadeOut);
                                    llControlPanel.setVisibility(View.INVISIBLE);
                                }
                                else {
                                    llControlPanel.startAnimation(animFadeIn);
                                    llControlPanel.setVisibility(View.VISIBLE);
                                }
                                isShowPanel ^= true;
                            }
                        }
                        break;
                }
                return true;
            }

            private double getDistance(MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return Math.sqrt(x * x + y * y);
            }
        });

        String mediaUrl = data.toString();
        media = MediaFactory.newInstance(mediaUrl);

        //とりあえず念のため見ておくか
        if (mediaUrl == null || media == null) {
            Toast.makeText(PreviewActivity.this, "画像の読み込みに失敗しました", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        final Handler handler = new Handler();
        loaderTask = new ParallelAsyncTask<String, Object, Bitmap>() {
            class Callback {
                public int received, contentLength = -1;
                public long beginTime, currentTime;
            }

            @Override
            protected Bitmap doInBackground(String... params) {
                String url = params[0];

                // 保存ボタンの有効化
                handler.post(() -> {
                    if (ibSave != null) {
                        ibSave.setEnabled(true);
                    }
                });

                int exifRotate = 0;
                if (url.startsWith("content://")) {
                    try {
                        exifRotate = BitmapUtil.getExifRotate(getContentResolver().openInputStream(Uri.parse(url)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //キャッシュディレクトリを取得
                File cacheDir;
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    cacheDir = getExternalCacheDir();
                }
                else {
                    cacheDir = getCacheDir();
                }
                cacheDir = new File(cacheDir, "preview");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                //キャッシュファイル名を生成
                String fileKey = StringUtil.generateKey(url);
                File cacheFile = new File(cacheDir, fileKey);
                // キャッシュディレクトリにファイルが無い場合、もしくはキャッシュが保存されてから
                // 1日以上経過している場合はダウンロードを行う
                if (!cacheFile.exists() || cacheFile.lastModified() < System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS) {
                    InputStream input;
                    Callback callback = new Callback();
                    Media.ResolveInfo resolveInfo = null;
                    if (url.startsWith("content://") || url.startsWith("file://")) {
                        try {
                            input = getContentResolver().openInputStream(Uri.parse(url));
                            callback.beginTime = System.currentTimeMillis();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            return null;
                        }
                    } else if (isDMImage(url)) {
                        while (!isTwitterServiceBound() || getTwitterService() == null) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignore) {}
                        }
                        try {
                            Twitter twitter = getTwitterService().getTwitterOrThrow(user);
                            input = twitter.getDMImageAsStream(url);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return null;
                        }
                        callback.beginTime = System.currentTimeMillis();
                    } else {
                        try {
                            resolveInfo = media.resolveMedia();
                            callback.contentLength = resolveInfo.getContentLength();
                            callback.beginTime = System.currentTimeMillis();
                            input = resolveInfo.getStream();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }
                    try {
                        FileOutputStream output = new FileOutputStream(cacheFile);
                        byte[] buf = new byte[4096];
                        int length;
                        while ((length = input.read(buf, 0, buf.length)) != -1) {
                            output.write(buf, 0, length);
                            callback.received += length;
                            callback.currentTime = System.currentTimeMillis();
                            if (isCancelled()) {
                                output.close();
                                cacheFile.delete();
                                return null;
                            } else {
                                publishProgress(callback);
                            }
                        }
                        output.close();
                        System.gc();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    } finally {
                        try {
                            input.close();
                        } catch (IOException ignore) {}
                        if (resolveInfo != null) {
                            resolveInfo.dispose();
                        }
                    }
                }
                try {
                    //画像サイズを確認
                    FileInputStream fis = new FileInputStream(cacheFile);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(fis, null, options);
                    fis.close();
                    //実際の読み込みを行う
                    fis = new FileInputStream(cacheFile);
                    options.inJustDecodeBounds = false;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT && Math.max(options.outWidth, options.outHeight) > 1500) {
                        int scaleW = options.outWidth / 1500;
                        int scaleH = options.outHeight / 1500;
                        options.inSampleSize = Math.max(scaleW, scaleH);
                    } else if (Math.max(options.outWidth, options.outHeight) > 2048) {
                        int scaleW = options.outWidth / 2048;
                        int scaleH = options.outHeight / 2048;
                        options.inSampleSize = Math.max(scaleW, scaleH);
                    }
                    Bitmap bitmap = BitmapFactory.decodeStream(fis, null, options);
                    fis.close();
                    if (bitmap == null) {
                        cacheFile.delete();
                        return null;
                    }
                    if (exifRotate > 0) {
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        Matrix matrix = new Matrix();
                        matrix.postRotate(exifRotate, width / 2, height / 2);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    }
                    return bitmap;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                Callback callback = (Callback) values[0];
                int progress = callback.received * 100 / callback.contentLength;
                long elapsed = (callback.currentTime - callback.beginTime) / 1000;
                if (elapsed < 1) {
                    elapsed = 1;
                }
                if (callback.contentLength < 1) {
                    loadProgressText.setText("");
                    loadProgressText2.setText(String.format(Locale.US, "%d KB\n%dKB/s",
                            (callback.received / 1024),
                            (callback.received / 1024) / elapsed));
                } else {
                    loadProgressText.setText(String.format(Locale.US, "%d%%", progress));
                    loadProgressText2.setText(String.format(Locale.US, "%d/%d KB\n%dKB/s",
                            (callback.received / 1024),
                            (callback.contentLength / 1024),
                            (callback.received / 1024) / elapsed));
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                loadProgressText.setVisibility(View.GONE);
                loadProgressText2.setVisibility(View.GONE);

                if (isCancelled()) {
                    return;
                }
                else if (bitmap == null) {
                    Toast.makeText(PreviewActivity.this, "画像の読み込みに失敗しました", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                image = bitmap;
                imageView.setImageBitmap(bitmap);
                //画面解像度を取得して初期サイズ設定
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                displayWidth = displayMetrics.widthPixels;
                displayHeight = displayMetrics.heightPixels;
                float scale = 1.0f;
                if (bitmap.getWidth() > bitmap.getHeight() && displayWidth < bitmap.getWidth()) {
                    scale = (float) displayWidth / bitmap.getWidth();
                }
                else if (displayHeight < bitmap.getHeight()) {
                    scale = (float) displayHeight / bitmap.getHeight();
                }
                minScale = scale;

                matrix = new Matrix();
                matrix.setTranslate(-(image.getWidth() / 2), -(image.getHeight() / 2));
                matrix.postScale(scale, scale);
                matrix.postTranslate(displayWidth / 2, displayHeight / 2);
                updateMatrix();
                //QR解析
                processZxing();
            }
        };
        loaderTask.executeParallel(mediaUrl);

        Object anyStatus = getIntent().getSerializableExtra(EXTRA_STATUS);
        if (anyStatus instanceof TwitterStatus) {
            status = (TwitterStatus) anyStatus;
        } else if (anyStatus instanceof PreformedStatus) {
            status = new TwitterStatus((PreformedStatus) anyStatus, ((PreformedStatus) anyStatus).getRepresentUser());
        } else {
            status = null;
        }
        if (status != null && status.isRepost()) {
            status = status.getOriginStatus();
        }

        ImageButton ibRotateLeft = (ImageButton) findViewById(R.id.ibPreviewRotateLeft);
        ibRotateLeft.setOnClickListener(v -> {
            if (image != null) {
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setTranslate(-(image.getWidth() / 2), -(image.getHeight() / 2));
                rotateMatrix.postRotate(-90f);
                rotateMatrix.postTranslate((image.getWidth() / 2), (image.getHeight() / 2));
                matrix.preConcat(rotateMatrix);
                updateMatrix();
            }
        });
        ImageButton ibRotateRight = (ImageButton) findViewById(R.id.ibPreviewRotateRight);
        ibRotateRight.setOnClickListener(v -> {
            if (image != null) {
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setTranslate(-(image.getWidth() / 2), -(image.getHeight() / 2));
                rotateMatrix.postRotate(90f);
                rotateMatrix.postTranslate((image.getWidth() / 2), (image.getHeight() / 2));
                matrix.preConcat(rotateMatrix);
                updateMatrix();
            }
        });

        ImageButton ibBrowser = (ImageButton) findViewById(R.id.ibPreviewBrowser);
        ibBrowser.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, data), null)));

        ibSave.setEnabled(false); // 実体解決完了まで無効化
        ibSave.setOnClickListener(v -> {
            PreviewActivityPermissionsDispatcher.onClickSaveWithPermissionCheck(PreviewActivity.this);
        });

        if (!mediaUrl.startsWith("http") || isDMImage(mediaUrl)) {
            ibBrowser.setEnabled(false);
            ibBrowser.setVisibility(View.GONE);
            ibSave.setVisibility(View.GONE);
        }

        if (status != null) {
            tweetView.setMode(StatusView.Mode.PREVIEW);
            tweetView.setStatus(status);
        } else {
            tweetView.setVisibility(View.GONE);
        }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void onClickSave() {
        new ParallelAsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    DownloadManager dlm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    DownloadManager.Request request = media.getDownloadRequest();
                    if (request == null) {
                        return "保存できない種類の画像です。";
                    }
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                    File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    pathExternalPublicDir.mkdirs();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    dlm.enqueue(request);
                    return "";
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return "保存に失敗しました。";
            }

            @Override
            protected void onPostExecute(String str) {
                if (!TextUtils.isEmpty(str)) {
                    Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
                }
            }
        }.executeParallel();
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void onDeniedReadExternalStorage() {
        if (PermissionUtils.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, "ストレージにアクセスする権限がありません。", Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("許可が必要")
                    .setMessage("画像を保存するには、手動で設定画面からストレージへのアクセスを許可する必要があります。")
                    .setPositiveButton("設定画面へ", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    })
                    .setNegativeButton("キャンセル", (dialog, which) -> {
                    })
                    .create()
                    .show();
        }
    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showRationaleForReadExternalStorage(final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("許可が必要")
                .setMessage("画像を保存するためには、ストレージへのアクセス許可が必要です。")
                .setPositiveButton("許可", (dialog, which) -> {
                    request.proceed();
                })
                .setNegativeButton("許可しない", (dialog, which) -> {
                    request.cancel();
                })
                .create()
                .show();
    }

    private void processZxing() {
        try {
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            LuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                Result result = new MultiFormatReader().decode(binaryBitmap);
                llQrText.setVisibility(View.VISIBLE);
                tvQrText.setText(result.getText());
            } catch (NotFoundException e) {
                llQrText.setVisibility(View.GONE);
            }
        } catch (OutOfMemoryError e) {
            // そんなこともある
            System.gc();
            llQrText.setVisibility(View.GONE);
        }
    }

    private boolean isDMImage(String url) {
        return url.startsWith("https://ton.twitter.com/") || url.contains("twitter.com/messages/media/");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loaderTask != null) {
            loaderTask.cancel(true);
        }
        if (imageView != null) {
            imageView.setImageDrawable(null);
            imageView = null;
        }
        if (image != null && !image.isRecycled()) {
            image.recycle();
            System.gc();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PreviewActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    private void updateMatrix() {
        imageView.setImageMatrix(matrix);
        imageView.invalidate();
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}
}
