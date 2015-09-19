package shibafu.yukari.media;

import totoridipjp4j.TotoriDipJPFactory;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Totori extends LinkMedia {
    private String sourceURL = null;

    public Totori(String mediaURL) {
        super(mediaURL);
    }

    private String getSourceURL(final String browseURL) {
        if (sourceURL == null) {
            sourceURL = fetchSynced(() -> new TotoriDipJPFactory().getInstance().getTopImg().getUrl());
        }
        return sourceURL;
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
