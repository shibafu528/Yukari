package shibafu.yukari.plugin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import shibafu.yukari.twitter.TwitterUtil;

/**
 * Created by shibafu on 15/01/07.
 */
public class UserPluginActivity extends Activity {

    public static final String TO_TWILOG  = "shibafu.yukari.plugin.OpenTwilogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String componentName = intent.getComponent().getClassName();
        String screenName = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (componentName != null) {
            switch (componentName) {
                case TO_TWILOG:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getTwilogURL(screenName))));
                    break;
            }
        }
        finish();
    }
}
