package shibafu.yukari.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import twitter4j.Status;

/**
 * Created by Shibafu on 13/09/22.
 */
public class PreviewActivity extends FragmentActivity {

    public static final String EXTRA_STATUS = "status";

    private Bitmap image;
    private int translateX, translateY;
    private float scale = 1.0f;
    private float rotate = 0.0f;

    private AsyncTask<String, Void, Bitmap> loaderTask = null;

    private SmartImageView imageView;
    private View tweetView;
    private PreformedStatus status;

    private Animation animFadeIn, animFadeOut;
    private boolean isShowPanel = true;

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
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });

        final LinkMedia linkMedia = LinkMediaFactory.createLinkMedia(data.toString());
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
                //キャッシュディレクトリにファイルが無い場合ダウンロードを行う
                if (!cacheFile.exists()) {
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
                    return;
                }
                image = bitmap;
                imageView.setImageBitmap(bitmap);
                //画面解像度を取得して初期サイズ設定
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                int displayWidth = display.getWidth();
                int displayHeight = display.getHeight();
                if (bitmap.getWidth() > bitmap.getHeight() && displayWidth < bitmap.getWidth()) {
                    scale = (float) displayWidth / bitmap.getWidth();
                }
                else if (displayHeight < bitmap.getHeight()) {
                    scale = (float) displayHeight / bitmap.getHeight();
                }
                translateX = displayWidth / 2;
                translateY = displayHeight / 2;
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
                rotate -= 90f;
                updateMatrix();
            }
        });
        ImageButton ibRotateRight = (ImageButton) findViewById(R.id.ibPreviewRotateRight);
        ibRotateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotate += 90;
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
                    TweetAdapterWrap.setStatusToView(PreviewActivity.this, tweetView, status, null,
                            TweetAdapterWrap.CONFIG_DISABLE_BGCOLOR | TweetAdapterWrap.CONFIG_DISABLE_FONTCOLOR);
                }
            });
        }
    }

    private void updateMatrix() {
        Matrix matrix = new Matrix();
        matrix.setTranslate(-(image.getWidth() * scale / 2), -(image.getHeight() * scale / 2));
        matrix.postRotate(rotate);
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);
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
        }
    }
}
