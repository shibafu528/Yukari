package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Lockerz extends LinkMedia {

    public Lockerz(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://lockerz\\.com/s/([a-zA-Z0-9]+)",
                "http://api.plixi.com/api/tpapi.svc/imagefromurl?url=http://plixi.com/p/%1&size=big");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://lockerz\\.com/s/([a-zA-Z0-9]+)",
                "http://api.plixi.com/api/tpapi.svc/imagefromurl?url=http://plixi.com/p/%1&size=thumbnail");
        return matcher.getFullPageUrl(browseURL);
    }
}
