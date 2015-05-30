package shibafu.dissonance.twitter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import shibafu.dissonance.twitter.statusimpl.PreformedStatus;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterUtil {

    @Deprecated
    /**
     * <b>TwitterService上での初期化以外で呼び出さないこと。</b>
     * それ以外の場所ではTwitterServiceをバインドし取得すること。
     */
	public static Twitter getTwitterInstance(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		String consumer_key = sp.getString("twitter_consumer_key", "");
		String consumer_secret = sp.getString("twitter_consumer_secret", "");

        ConfigurationBuilder configuration = new ConfigurationBuilder();

		TwitterFactory factory = new TwitterFactory(configuration.build());
		Twitter twitter = factory.getInstance();
		twitter.setOAuthConsumer(consumer_key, consumer_secret);

		return twitter;
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
        return "http://aclog.koba789.com/" + screenName + "/timeline";
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
        sb.append("http://twitter.com/");
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
