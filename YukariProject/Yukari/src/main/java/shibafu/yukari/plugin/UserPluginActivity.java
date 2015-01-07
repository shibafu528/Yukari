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

    private static final String TO_TWILOG  = "shibafu.yukari.plugin.OpenTwilogActivity";
    private static final String TO_FAVSTAR = "shibafu.yukari.plugin.OpenFavstarActivity";
    private static final String TO_ACLOG   = "shibafu.yukari.plugin.OpenAclogUserActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String componentName = intent.getComponent().getClassName();
        if (componentName != null) {
            switch (componentName) {
                case TO_TWILOG:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getTwilogURL(intent.getStringExtra(Intent.EXTRA_TEXT)))));
                    break;
                case TO_FAVSTAR:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getFavstarURL(intent.getStringExtra(Intent.EXTRA_TEXT)))));
                    break;
                case TO_ACLOG:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getAclogURL(intent.getStringExtra(Intent.EXTRA_TEXT)))));
                    break;
            }
        }
        finish();
    }
}
