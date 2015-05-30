package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class NicoSeiga extends LinkMedia {

    public NicoSeiga(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://(seiga\\.nicovideo\\.jp\\/seiga|nico\\.ms)\\/im(\\d+)", "http://lohas.nicoseiga.jp/thumb/%2l?");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://(seiga\\.nicovideo\\.jp\\/seiga|nico\\.ms)\\/im(\\d+)", "http://lohas.nicoseiga.jp/thumb/%2i?");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
