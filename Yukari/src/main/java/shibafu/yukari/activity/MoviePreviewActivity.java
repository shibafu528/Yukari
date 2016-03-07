package shibafu.yukari.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import shibafu.yukari.af2015.R;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.LinkMediaFactory;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Created by shibafu on 14/06/19.
 */
public class MoviePreviewActivity extends FragmentActivity{

    public static final String EXTRA_STATUS = "status";

    private View tweetView;
    private PreformedStatus status;
    private TweetAdapterWrap.ViewConverter viewConverter;

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
            videoView.seekTo(0);
            videoView.start();
        });

        LinkMedia linkMedia = LinkMediaFactory.newInstance(data.toString());
        if (linkMedia == null) {
            Toast.makeText(this, "null media", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new ParallelAsyncTask<LinkMedia, Void, String>() {
            @Override
            protected String doInBackground(LinkMedia... params) {
                return params[0].getMediaURL();
            }

            @Override
            protected void onPostExecute(String s) {
                try {
                    videoView.setVideoURI(Uri.parse(s));
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),
                            "このメディアを開くことが出来ません。\n元ツイートのパーマリンクを添えて作者に連絡してみるといいかもしれません。",
                            Toast.LENGTH_LONG).show();
                }
            }
        }.executeParallel(linkMedia);

        status = (PreformedStatus) getIntent().getSerializableExtra(EXTRA_STATUS);
        if (status != null && status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        tweetView = findViewById(R.id.inclPreviewStatus);
        viewConverter = TweetAdapterWrap.ViewConverter.newInstance(
                this,
                null,
                null,
                PreferenceManager.getDefaultSharedPreferences(this),
                PreformedStatus.class);

        findViewById(R.id.ibPreviewBrowser).setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, data), null)));
    }

    @Override
    public void onResume() {
        super.onResume();

        if (status != null) {
            new android.os.Handler().post(() -> viewConverter.convertView(tweetView, status, TweetAdapterWrap.ViewConverter.MODE_PREVIEW));
        }
    }
}
