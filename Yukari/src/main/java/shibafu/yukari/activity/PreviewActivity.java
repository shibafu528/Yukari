package shibafu.yukari.activity;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Handler;

import shibafu.util.TweetImageUrl;
import shibafu.yukari.R;
import shibafu.yukari.common.TweetAdapterWrap;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/09/22.
 */
public class PreviewActivity extends Activity {

    public static final String EXTRA_STATUS = "status";
    private View tweetView;
    private Status status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imagepreview);

        Uri data = getIntent().getData();
        if (data == null) {
            Toast.makeText(this, "null uri", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SmartImageView imageView = (SmartImageView) findViewById(R.id.ivPreviewImage);

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

        String expandedUrl = TweetImageUrl.getFullImageUrl(data.toString());
        if (expandedUrl != null) {
            imageView.setImageUrl(expandedUrl);
            checkTask.execute(expandedUrl);
        }
        else {
            String s = data.toString();
            imageView.setImageUrl(s);
            checkTask.execute(s);
        }

        status = (Status) getIntent().getSerializableExtra(EXTRA_STATUS);
        tweetView = findViewById(R.id.inclPreviewStatus);
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
}
