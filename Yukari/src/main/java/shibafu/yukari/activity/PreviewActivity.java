package shibafu.yukari.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;
import permissions.dispatcher.RuntimePermissions;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.databinding.ActivityImagepreviewBinding;
import shibafu.yukari.entity.Status;
import shibafu.yukari.media2.Media;
import shibafu.yukari.media2.MediaFactory;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.util.BitmapUtil;
import shibafu.yukari.util.StringUtil;
import shibafu.yukari.view.StatusView;
import twitter4j.Twitter;
import twitter4j.TwitterException;

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

    private static final int[] ORIENTATIONS = {
            SubsamplingScaleImageView.ORIENTATION_0,
            SubsamplingScaleImageView.ORIENTATION_90,
            SubsamplingScaleImageView.ORIENTATION_180,
            SubsamplingScaleImageView.ORIENTATION_270,
    };

    private ActivityImagepreviewBinding binding;

    private Media media;

    private ParallelAsyncTask<String, Object, Bitmap> loaderTask = null;

    private Status status;
    private AuthUserRecord user;

    private Animation animFadeIn, animFadeOut;
    private boolean isShowPanel = true;

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        binding = ActivityImagepreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

        animFadeIn = AnimationUtils.loadAnimation(this, R.anim.anim_fadein);
        animFadeOut = AnimationUtils.loadAnimation(this, R.anim.anim_fadeout);

        final LinearLayout llControlPanel = (LinearLayout) findViewById(R.id.llPreviewPanel);
        binding.ivPreviewImage.setMinimumDpi(80);
        binding.ivPreviewImage.setOnClickListener(view -> {
            if (isShowPanel) {
                llControlPanel.startAnimation(animFadeOut);
                llControlPanel.setVisibility(View.INVISIBLE);
            } else {
                llControlPanel.startAnimation(animFadeIn);
                llControlPanel.setVisibility(View.VISIBLE);
            }
            isShowPanel ^= true;
        });
        binding.ivPreviewImage.setOnStateChangedListener(new SubsamplingScaleImageView.OnStateChangedListener() {
            @Override
            public void onScaleChanged(float newScale, int origin) {
                if (isShowPanel) {
                    llControlPanel.startAnimation(animFadeOut);
                    llControlPanel.setVisibility(View.INVISIBLE);
                    isShowPanel = false;
                }
            }

            @Override
            public void onCenterChanged(PointF newCenter, int origin) {
                if (isShowPanel) {
                    llControlPanel.startAnimation(animFadeOut);
                    llControlPanel.setVisibility(View.INVISIBLE);
                    isShowPanel = false;
                }
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
                    if (binding.ibPreviewSave != null) {
                        binding.ibPreviewSave.setEnabled(true);
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
                    binding.tvPreviewProgress.setText("");
                    binding.tvPreviewProgress2.setText(String.format(Locale.US, "%d KB\n%dKB/s",
                            (callback.received / 1024),
                            (callback.received / 1024) / elapsed));
                } else {
                    binding.tvPreviewProgress.setText(String.format(Locale.US, "%d%%", progress));
                    binding.tvPreviewProgress2.setText(String.format(Locale.US, "%d/%d KB\n%dKB/s",
                            (callback.received / 1024),
                            (callback.contentLength / 1024),
                            (callback.received / 1024) / elapsed));
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                binding.tvPreviewProgress.setVisibility(View.GONE);
                binding.tvPreviewProgress2.setVisibility(View.GONE);

                if (isCancelled()) {
                    return;
                }
                else if (bitmap == null) {
                    Toast.makeText(PreviewActivity.this, "画像の読み込みに失敗しました", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                // 本当はBitmapではなくキャッシュファイルパスを渡したほうがライブラリのポテンシャルを引き出せる
                binding.ivPreviewImage.setImage(ImageSource.bitmap(bitmap));
                //QR解析
                processZxing(bitmap);
            }
        };
        loaderTask.executeParallel(mediaUrl);

        Object anyStatus = getIntent().getSerializableExtra(EXTRA_STATUS);
        if (anyStatus instanceof TwitterStatus) {
            status = (TwitterStatus) anyStatus;
        } else {
            status = null;
        }
        if (status != null && status.isRepost()) {
            status = status.getOriginStatus();
        }

        ImageButton ibRotateLeft = findViewById(R.id.ibPreviewRotateLeft);
        ibRotateLeft.setOnClickListener(v -> {
            int orientation = binding.ivPreviewImage.getAppliedOrientation();
            for (int i = ORIENTATIONS.length - 1; i >= 0; i--) {
                if (ORIENTATIONS[i] < orientation) {
                    binding.ivPreviewImage.setOrientation(ORIENTATIONS[i]);
                    return;
                }
            }
            // if orientation <= 0
            binding.ivPreviewImage.setOrientation(SubsamplingScaleImageView.ORIENTATION_270);
        });
        ImageButton ibRotateRight = findViewById(R.id.ibPreviewRotateRight);
        ibRotateRight.setOnClickListener(v -> {
            int orientation = binding.ivPreviewImage.getAppliedOrientation();
            for (int i = 0; i < ORIENTATIONS.length; i++) {
                if (ORIENTATIONS[i] > orientation) {
                    binding.ivPreviewImage.setOrientation(ORIENTATIONS[i]);
                    return;
                }
            }
            // if orientation >= 270
            binding.ivPreviewImage.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
        });

        ImageButton ibBrowser = (ImageButton) findViewById(R.id.ibPreviewBrowser);
        ibBrowser.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, data), null)));

        binding.ibPreviewSave.setEnabled(false); // 実体解決完了まで無効化
        binding.ibPreviewSave.setOnClickListener(v -> {
            PreviewActivityPermissionsDispatcher.onClickSaveWithPermissionCheck(PreviewActivity.this);
        });

        if (!mediaUrl.startsWith("http") || isDMImage(mediaUrl)) {
            ibBrowser.setEnabled(false);
            ibBrowser.setVisibility(View.GONE);
            binding.ibPreviewSave.setVisibility(View.GONE);
        }

        if (status != null) {
            binding.twvPreviewStatus.setMode(StatusView.Mode.PREVIEW);
            binding.twvPreviewStatus.setStatus(status);
        } else {
            binding.twvPreviewStatus.setVisibility(View.GONE);
        }
    }

    @NeedsPermission(value = Manifest.permission.WRITE_EXTERNAL_STORAGE, maxSdkVersion = Build.VERSION_CODES.P)
    void onClickSave() {
        new SaveAsyncTask(getApplicationContext()).doInBackground(media);
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

    private void processZxing(Bitmap image) {
        try {
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            LuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                Result result = new MultiFormatReader().decode(binaryBitmap);
                binding.llQrText.setVisibility(View.VISIBLE);
                binding.tvQrText.setText(result.getText());
            } catch (NotFoundException e) {
                binding.llQrText.setVisibility(View.GONE);
            }
        } catch (OutOfMemoryError e) {
            // そんなこともある
            System.gc();
            binding.llQrText.setVisibility(View.GONE);
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
        binding.ivPreviewImage.recycle();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PreviewActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    private static class SaveAsyncTask extends ParallelAsyncTask<Media, Void, String> {
        private final Context context;

        SaveAsyncTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Media... media) {
            try {
                DownloadManager dlm = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
                DownloadManager.Request request = media[0].getDownloadRequest();
                if (request == null) {
                    return "保存できない種類の画像です。";
                }
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
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
                Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
