package shibafu.dissonance.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import shibafu.dissonance.R;

/**
 * Created by shibafu on 14/02/01.
 */
public class TweetShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent shortcutIntent = new Intent(this, TweetActivity.class);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher_tweet));
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.title_activity_tweet));

        setResult(RESULT_OK, intent);
        finish();
    }
}
