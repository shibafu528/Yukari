package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Photozou extends LinkMedia {

    public Photozou(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://photozou\\.jp/photo/show/([a-zA-Z0-9]+)/([a-zA-Z0-9]+)", "http://photozou.jp/p/img/%2");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://photozou\\.jp/photo/show/([a-zA-Z0-9]+)/([a-zA-Z0-9]+)", "http://photozou.jp/p/thumb/%2");
        return matcher.getFullPageUrl(browseURL);
    }
}
