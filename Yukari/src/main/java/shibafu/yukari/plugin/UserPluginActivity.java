package shibafu.yukari.plugin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import shibafu.yukari.twitter.TwitterUtil;
import twitter.intent.TwitterIntent;
import twitter.intent.TwitterShare;
import twitter.intent.UserIntent;

/**
 * Created by shibafu on 15/01/07.
 */
public class UserPluginActivity extends Activity {

    public static final String TO_TWILOG  = "shibafu.yukari.plugin.OpenTwilogActivity";
    public static final String TO_FAVSTAR = "shibafu.yukari.plugin.OpenFavstarActivity";
    public static final String TO_ACLOG   = "shibafu.yukari.plugin.OpenAclogUserActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String componentName = intent.getComponent().getClassName();
        String screenName;
        switch (intent.getAction()) {
            case TwitterIntent.ACTION_SHOW_USER:
                UserIntent userIntent = (UserIntent) TwitterShare.getTwitterIntent(intent);
                screenName = userIntent.getScreenName();
                break;
            default:
                screenName = intent.getStringExtra(Intent.EXTRA_TEXT);
                break;
        }
        if (componentName != null) {
            switch (componentName) {
                case TO_TWILOG:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getTwilogURL(screenName))));
                    break;
                case TO_FAVSTAR:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getFavstarURL(screenName))));
                    break;
                case TO_ACLOG:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(TwitterUtil.getAclogURL(screenName))));
                    break;
            }
        }
        finish();
    }
}
