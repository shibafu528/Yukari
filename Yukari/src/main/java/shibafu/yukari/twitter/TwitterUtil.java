package shibafu.yukari.twitter;

import android.content.Context;
import android.support.annotation.NonNull;
import shibafu.yukari.R;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterUtil {

    /**
     * CK/CSの設定を行ったTwitterFactoryのインスタンスを生成します。
     * @param context Application Context
     * @return TwitterFactory instance
     */
    public static TwitterFactory getTwitterFactory(Context context) {
        ConfigurationBuilder configuration = getConfigurationBuilder(context);

        return new TwitterFactory(configuration.build());
    }

    @NonNull
    private static ConfigurationBuilder getConfigurationBuilder(Context context) {
        String consumer_key = context.getString(R.string.twitter_consumer_key);
        String consumer_secret = context.getString(R.string.twitter_consumer_secret);

        ConfigurationBuilder configuration = new ConfigurationBuilder();
        configuration.setOAuthConsumerKey(consumer_key);
        configuration.setOAuthConsumerSecret(consumer_secret);
        configuration.setTweetModeExtended(true);
        return configuration;
    }

    public static String getProfileUrl(String screenName) {
        return "https://twitter.com/" + screenName;
    }

    //<editor-fold desc="外部サービスURL生成">
    public static String getTwilogURL(String screenName) {
        if (screenName.startsWith("@")) {
            screenName = screenName.substring(1);
        }
        return "http://twilog.org/" + screenName;
    }
    //</editor-fold>

    /**
     * TwitterのStatus URLをパースし、IDを取得します。
     * @param url Status URL
     * @return Status ID, 不正なURLの場合は -1
     */
    public static long getStatusIdFromUrl(String url) {
        Pattern pattern = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?(?:\\?.+)?\\$");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String id = matcher.group(1);
            try {
                return Long.valueOf(id);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
