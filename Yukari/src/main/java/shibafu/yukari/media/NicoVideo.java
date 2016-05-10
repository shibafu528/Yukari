package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class NicoVideo extends LinkMedia {

    public NicoVideo(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL;
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://(www\\.nicovideo\\.jp\\/watch|nico\\.ms)\\/[sn][mo]([1-9]\\d*)", "http://tn-skr$.smilevideo.jp/smile?i=%2");
        String url = matcher.getFullPageUrl(browseURL);
        if (url == null) return null;
        return url.replace("$", String.valueOf((Integer.valueOf(matcher.getMatchGroup()[1]) % 4) + 1));
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
