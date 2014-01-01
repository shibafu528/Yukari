package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Instagram extends LinkMedia {

    public Instagram(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String extractThumbURL(String mediaURL) {
        ImageMatch matcher = new ImageMatch("http://instagr.am/p/([a-zA-Z0-9]+)", "http://instagr.am/p/%1/media/?size=l");
        return matcher.getFullPageUrl(mediaURL);
    }
}
