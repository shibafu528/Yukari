package shibafu.yukari.activity;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import shibafu.yukari.R;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.LinkMediaFactory;
import shibafu.yukari.twitter.PreformedStatus;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/09/22.
 */
public class PreviewActivity extends Activity {

    public static final String EXTRA_STATUS = "status";
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

        //存在チェックの処理用
        AsyncTask<String, Void, Boolean> checkTask = new AsyncTask<String, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(String... params) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(params[0]).openConnection();
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();
                    inputStream.read();
                    inputStream.close();

                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                if (!aBoolean) {
                    Toast.makeText(PreviewActivity.this, "画像の取得に失敗", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        };

        final LinkMedia linkMedia = LinkMediaFactory.createLinkMedia(data.toString());
        if (linkMedia != null) {
            imageView.setImageUrl(linkMedia.getMediaURL());
            checkTask.execute(linkMedia.getMediaURL());
        }
        else {
            String s = data.toString();
            imageView.setImageUrl(s);
            checkTask.execute(s);
        }

        status = (PreformedStatus) getIntent().getSerializableExtra(EXTRA_STATUS);
        tweetView = findViewById(R.id.inclPreviewStatus);

        ImageButton ibRotateLeft = (ImageButton) findViewById(R.id.ibPreviewRotateLeft);
        ibRotateLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotate(-90);
            }
        });
        ImageButton ibRotateRight = (ImageButton) findViewById(R.id.ibPreviewRotateRight);
        ibRotateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotate(90);
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
                    Uri uri;
                    if (linkMedia != null) {
                        uri = Uri.parse(linkMedia.getMediaURL());
                    }
                    else {
                        uri = Uri.parse(data.toString());
                    }
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

    private void rotate(int degree) {
    }
}
