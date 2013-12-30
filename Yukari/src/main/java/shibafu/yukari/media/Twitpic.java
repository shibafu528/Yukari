package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twitpic extends LinkMedia {

    public Twitpic(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String extractThumbURL(String mediaURL) {
        ImageMatch matcher = new ImageMatch("http://twitpic\\.com/([a-zA-Z0-9]+)", "http://twitpic.com/show/full/%1");
        return matcher.getFullPageUrl(mediaURL);
    }
}
