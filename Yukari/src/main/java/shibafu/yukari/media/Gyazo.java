package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Gyazo extends LinkMedia{

    public Gyazo(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://(?:www\\.|i\\.)?gyazo\\.com/(\\w+)(?:\\.png)?", "http://gyazo.com/%1.png");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://(?:www\\.|i\\.)?gyazo\\.com/(\\w+)(?:\\.png)?", "http://gyazo.com/thumb/%1.png");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
