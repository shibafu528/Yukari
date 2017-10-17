package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;
import totoridipjp4j.TotoriDipJPFactory;

import java.io.IOException;

public class Totori extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Totori(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        try {
            return new TotoriDipJPFactory().getInstance().getTopImg().getUrl();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "http://totori.dip.jp/imgs/totori_vita.jpg";
        }
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return resolveMediaUrl();
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
