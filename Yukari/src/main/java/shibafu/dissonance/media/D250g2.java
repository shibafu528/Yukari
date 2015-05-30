package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class D250g2 extends LinkMedia {

    public D250g2(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return "http://d250g2.com/d250g2.jpg";
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return "http://yukari.shibafu528.info/d250g2.png";
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
