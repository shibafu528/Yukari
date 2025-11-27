package shibafu.yukari.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import shibafu.yukari.R;

/**
 * Created by shibafu on 14/02/01.
 */
public class TweetShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent shortcutIntent = new Intent(this, TweetActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        ShortcutInfoCompat si = new ShortcutInfoCompat.Builder(this, "tweet")
                .setShortLabel(getString(R.string.title_activity_tweet))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher_tweet))
                .setIntent(shortcutIntent)
                .build();
        Intent shortcutResultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, si);

        setResult(RESULT_OK, shortcutResultIntent);
        finish();
    }
}
