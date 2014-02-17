package shibafu.yukari.activity;

import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.FloatMath;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import shibafu.yukari.R;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.LinkMediaFactory;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.util.StringUtil;

/**
 * Created by Shibafu on 13/09/22.
 */
public class PreviewActivity extends FragmentActivity {

    public static final String EXTRA_STATUS = "status";

    private Bitmap image;
    private Matrix matrix;
    private float minScale = 1.0f;

    private AsyncTask<String, Void, Bitmap> loaderTask = null;

    private SmartImageView imageView;
    private View tweetView;
    private PreformedStatus status;

    private Animation animFadeIn, animFadeOut;
    private boolean isShowPanel = true;
    private int displayWidth;
    private int displayHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imagepreview);

        final Uri data = getIntent().getData();
        if (data == null) {
            Toast.makeText(this, "null uri", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        animFadeIn = AnimationUtils.loadAnimation(this, R.anim.anim_fadein);
        animFadeOut = AnimationUtils.loadAnimation(this, R.anim.anim_fadeout);

        final LinearLayout llControlPanel = (LinearLayout) findViewById(R.id.llPreviewPanel);
        imageView = (SmartImageView) findViewById(R.id.ivPreviewImage);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private static final int TOUCH_NONE = 0;
            private static final int TOUCH_DRAG = 1;
            private static final int TOUCH_ZOOM = 2;
            private int touchMode = TOUCH_NONE;

            private static final int DRAG_THRESHOLD = 30;
            private float dragStartX, dragStartY, dragPrevX, dragPrevY;
            private boolean begunDrag = false;

            private float pinchPrevDistance;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //TODO: 画像のロードが済んでいない時にもしこの辺のコードが呼ばれるとクラッシュする
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
                        if (touchMode == TOUCH_ZOOM) {
                            if (isShowPanel) {
                                llControlPanel.startAnimation(animFadeOut);
                                llControlPanel.setVisibility(View.INVISIBLE);
                                isShowPanel = false;
                            }

                            float distance = getDistance(event);
                            float scale = (distance - pinchPrevDistance) /
                                    FloatMath.sqrt(displayWidth * displayWidth + displayHeight * displayHeight);
                            pinchPrevDistance = distance;
                            scale += 1;
                            scale = scale * scale;

                            float[] matrixValues = new float[9];
                            matrix.getValues(matrixValues);
                            float nowScale = matrixValues[Matrix.MSCALE_X];
                            if (nowScale * scale > minScale) {
                                matrix.postScale(scale, scale);
                                matrix.postTranslate(-(displayWidth * scale - displayWidth) / 2,
                                        -(displayHeight * scale - displayHeight) / 2);
                                matrix.postTranslate(-(displayWidth/2 - displayWidth/2) * scale, 0);
                                matrix.postTranslate(0, -(displayHeight/2 - displayHeight/2) * scale);
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

            private float getDistance(MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return FloatMath.sqrt(x * x + y * y);
            }
        });

        final LinkMedia linkMedia = LinkMediaFactory.newInstance(data.toString());
        final String mediaUrl;
        if (linkMedia != null) {
            mediaUrl = linkMedia.getMediaURL();
        }
        else {
            mediaUrl = data.toString();
        }

        loaderTask = new AsyncTask<String, Void, Bitmap>() {
            LoadingDialogFragment dialogFragment;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                dialogFragment = new LoadingDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), null);
            }

            @Override
            protected Bitmap doInBackground(String... params) {
                String url = params[0];
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
                String fileKey = StringUtil.encodeKey(url);
                File cacheFile = new File(cacheDir, fileKey);
                // キャッシュディレクトリにファイルが無い場合、もしくはキャッシュが保存されてから
                // 1日以上経過している場合はダウンロードを行う
                if (!cacheFile.exists() || cacheFile.lastModified() < System.currentTimeMillis() - 86400000) {
                    try {
                        HttpURLConnection connection = (HttpURLConnection) new URL(params[0]).openConnection();
                        connection.connect();
                        InputStream input = connection.getInputStream();
                        FileOutputStream output = new FileOutputStream(cacheFile);
                        byte[] buf = new byte[4096];
                        int length;
                        while ((length = input.read(buf, 0, buf.length)) != -1) {
                            output.write(buf, 0, length);
                            if (isCancelled()) {
                                input.close();
                                output.close();
                                connection.disconnect();
                                cacheFile.delete();
                                return null;
                            }
                        }
                        output.close();
                        input.close();
                        connection.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
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
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB && Math.max(options.outWidth, options.outHeight) > 960) {
                        int scaleW = options.outWidth / 960;
                        int scaleH = options.outHeight / 960;
                        options.inSampleSize = Math.max(scaleW, scaleH);
                    }
                    else if (Math.max(options.outWidth, options.outHeight) > 1500) {
                        int scaleW = options.outWidth / 1500;
                        int scaleH = options.outHeight / 1500;
                        options.inSampleSize = Math.max(scaleW, scaleH);
                    }
                    Bitmap bitmap = BitmapFactory.decodeStream(fis, null, options);
                    fis.close();
                    return bitmap;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (dialogFragment != null) {
                    dialogFragment.dismiss();
                    dialogFragment = null;
                }
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
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                displayWidth = display.getWidth();
                displayHeight = display.getHeight();
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
            }
        };
        loaderTask.execute(mediaUrl);

        status = (PreformedStatus) getIntent().getSerializableExtra(EXTRA_STATUS);
        tweetView = findViewById(R.id.inclPreviewStatus);

        ImageButton ibRotateLeft = (ImageButton) findViewById(R.id.ibPreviewRotateLeft);
        ibRotateLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setTranslate(-(image.getWidth() / 2), -(image.getHeight() / 2));
                rotateMatrix.postRotate(-90f);
                rotateMatrix.postTranslate((image.getWidth() / 2), (image.getHeight() / 2));
                matrix.preConcat(rotateMatrix);
                updateMatrix();
            }
        });
        ImageButton ibRotateRight = (ImageButton) findViewById(R.id.ibPreviewRotateRight);
        ibRotateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setTranslate(-(image.getWidth() / 2), -(image.getHeight() / 2));
                rotateMatrix.postRotate(90f);
                rotateMatrix.postTranslate((image.getWidth() / 2), (image.getHeight() / 2));
                matrix.preConcat(rotateMatrix);
                updateMatrix();
            }
        });

        ImageButton ibBrowser = (ImageButton) findViewById(R.id.ibPreviewBrowser);
        ibBrowser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, data), null));
            }
        });

        ImageButton ibSave = (ImageButton) findViewById(R.id.ibPreviewSave);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            ibSave.setVisibility(View.GONE);
        }
        ibSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                    Toast.makeText(PreviewActivity.this, "Android2.3未満ではこのボタンは使えません\n今のところそういう仕様です", Toast.LENGTH_LONG).show();
                }
                else {
                    Uri uri = Uri.parse(mediaUrl);
                    DownloadManager dlm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                    String[] split = uri.getLastPathSegment().split("\\.");
                    if (split != null && split.length > 1) {
                        request.setMimeType("image/" + split[split.length-1]);
                    }
                    else {
                        //本当はこんなことせずちゃんとHTTPヘッダ読んだほうがいいと思ってる
                        uri = Uri.parse(uri.toString() + ".png");
                        request.setMimeType("image/png");
                    }
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.getLastPathSegment());
                    File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    pathExternalPublicDir.mkdirs();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    dlm.enqueue(request);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (status != null) {
            new android.os.Handler().post(new Runnable() {
                @Override
                public void run() {
                    TweetAdapterWrap.setStatusToView(
                            PreviewActivity.this, tweetView, status, null,
                            PreferenceManager.getDefaultSharedPreferences(PreviewActivity.this),
                            TweetAdapterWrap.MODE_PREVIEW);
                }
            });
        }
    }

    private void updateMatrix() {
        imageView.setImageMatrix(matrix);
        imageView.invalidate();
    }

    private class LoadingDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setIndeterminate(true);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage("読み込み中...");
            return dialog;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            if (loaderTask != null) {
                loaderTask.cancel(true);
            }
            finish();
        }
    }
}
