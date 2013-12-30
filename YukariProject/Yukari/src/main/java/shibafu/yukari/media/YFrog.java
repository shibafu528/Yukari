package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class YFrog extends LinkMedia {

    public YFrog(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String extractThumbURL(String mediaURL) {
        ImageMatch matcher = new ImageMatch("http://yfrog\\.com/([a-zA-Z0-9]+)", "http://yfrog.com/%1:medium");
        return matcher.getFullPageUrl(mediaURL);
    }
}
