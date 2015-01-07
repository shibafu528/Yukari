package shibafu.yukari.plugin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import twitter.share.lib.TwitterIntent;
import twitter.share.lib.TwitterShare;
import twitter.share.lib.intent.StatusIntent;

/**
 * Created by shibafu on 14/03/27.
 */
public class OpenAclogActivity extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String id;
        switch (intent.getAction()) {
            case TwitterIntent.ACTION_SHOW_STATUS:
                StatusIntent statusIntent = (StatusIntent) TwitterShare.INSTANCE.getTwitterIntent(intent);
                id = String.valueOf(statusIntent.getId());
                break;
            default:
                id = intent.getStringExtra("id");
                break;
        }

        Uri uri = Uri.withAppendedPath(Uri.parse("http://aclog.koba789.com/i"), id);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
        finish();
    }
}
