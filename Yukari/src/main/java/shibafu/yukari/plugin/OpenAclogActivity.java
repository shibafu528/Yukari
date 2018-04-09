package shibafu.yukari.plugin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Created by shibafu on 14/03/27.
 */
public class OpenAclogActivity extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String id = intent.getStringExtra("id");

        Uri uri = Uri.withAppendedPath(Uri.parse("http://aclog.rhe.jp/i"), id);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
        finish();
    }
}
