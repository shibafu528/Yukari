package shibafu.yukari.twitter;

import android.content.Context;
import android.support.annotation.NonNull;
import shibafu.yukari.R;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Status;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

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

    //<editor-fold desc="外部サービスURL生成">
    public static String getFavstarURL(String screenName) {
        if (screenName.startsWith("@")) {
            screenName = screenName.substring(1);
        }
        return "http://favstar.fm/users/" + screenName + "/recent";
    }

    public static String getAclogURL(String screenName) {
        if (screenName.startsWith("@")) {
            screenName = screenName.substring(1);
        }
        return "http://aclog.rhe.jp/" + screenName + "/timeline";
    }

    public static String getTwilogURL(String screenName) {
        if (screenName.startsWith("@")) {
            screenName = screenName.substring(1);
        }
        return "http://twilog.org/" + screenName;
    }
    //</editor-fold>

    //<editor-fold desc="StatusURL/Quote生成">
    public static String getTweetURL(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("https://twitter.com/");
        sb.append(status.getUser().getScreenName());
        sb.append("/status/");
        sb.append(status.getId());
        return sb.toString();
    }

    public static String createSTOT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.getUser().getScreenName());
        sb.append(":");
        sb.append((status instanceof PreformedStatus)? ((PreformedStatus) status).getPlainText() : status.getText());
        sb.append(" [");
        sb.append(getTweetURL(status));
        sb.append("]");
        return sb.toString();
    }

    public static String createQuotedRT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" RT @");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append((status instanceof PreformedStatus)? ((PreformedStatus) status).getPlainText() : status.getText());
        return sb.toString();
    }

    public static String createQT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" QT @");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append((status instanceof PreformedStatus)? ((PreformedStatus) status).getPlainText() : status.getText());
        return sb.toString();
    }

    public static String createQuote(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" \"@");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append((status instanceof PreformedStatus)? ((PreformedStatus) status).getPlainText() : status.getText());
        sb.append("\"");
        return sb.toString();
    }
    //</editor-fold>
}
