package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import shibafu.yukari.R;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.entity.Status;
import shibafu.yukari.media2.Media;
import shibafu.yukari.media2.MediaFactory;
import shibafu.yukari.media2.impl.TwitterVideo;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.view.StatusView;
import shibafu.yukari.view.TweetView;

/**
 * Created by shibafu on 14/06/19.
 */
public class MoviePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_STATUS = "status";

    private TweetView tweetView;
    private Status status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moviepreview);

        final Uri data = getIntent().getData();
        if (data == null) {
            Toast.makeText(this, "null uri", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        final VideoView videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> {
            // エラーした場合、onErrorListenerをセットしていないのでonCompletionListenerが呼ばれる
            if (mp.getDuration() <= 0 || mp.getCurrentPosition() != mp.getDuration()) {
                // そして死ね
                finish();
                return;
            }

            videoView.seekTo(0);
            videoView.start();
        });

        new ParallelAsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                Media media = MediaFactory.newInstance(params[0]);
                if (media instanceof TwitterVideo) {
                    // TwitterVideoはそのまま再生可能なURLを持つ。この前提が崩れたらもうAPIを考え直そう。
                    return media.getBrowseUrl();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                if (!TextUtils.isEmpty(s)) {
                    try {
                        videoView.setVideoURI(Uri.parse(s));
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // if error...
                new AlertDialog.Builder(MoviePreviewActivity.this)
                        .setTitle("エラー")
                        .setMessage("このメディアを開くことが出来ません。\n元ツイートのパーマリンクを添えて作者に連絡してみるといいかもしれません。")
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .create()
                        .show();
            }
        }.executeParallel(data.toString());

        Object anyStatus = getIntent().getSerializableExtra(EXTRA_STATUS);
        if (anyStatus instanceof TwitterStatus) {
            status = (TwitterStatus) anyStatus;
        } else {
            status = null;
        }
        if (status != null && status.isRepost()) {
            status = status.getOriginStatus();
        }

        tweetView = (TweetView) findViewById(R.id.twvPreviewStatus);
        if (status != null) {
            tweetView.setMode(StatusView.Mode.PREVIEW);
            tweetView.setStatus(status);
        }

        findViewById(R.id.ibPreviewBrowser).setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, data), null)));
    }
}
