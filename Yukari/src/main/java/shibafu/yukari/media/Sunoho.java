package shibafu.yukari.media;

/**
 * Created by shibafu on 14/09/14.
 */
public class Sunoho extends LinkMedia{

    public static final String PAGE_REGEX = "http://(?:gyazo\\.)?sunoho\\.com(?:/p)?/i/([a-f0-9]+)";
    public static final String REPLACE_PATTERN = "http://gyazo.sunoho.com/i/%1.png";

    public Sunoho(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch(PAGE_REGEX, REPLACE_PATTERN);
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch(PAGE_REGEX, REPLACE_PATTERN);
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
