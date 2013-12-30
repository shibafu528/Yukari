package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twipple extends LinkMedia {

    public Twipple(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String extractThumbURL(String mediaURL) {
        ImageMatch matcher = new ImageMatch("http://p\\.twipple\\.jp/([a-zA-Z0-9]+)", "http://p.twpl.jp/show/orig/%1");
        return matcher.getFullPageUrl(mediaURL);
    }
}
