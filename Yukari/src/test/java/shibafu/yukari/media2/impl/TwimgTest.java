package shibafu.yukari.media2.impl;

import junit.framework.Assert;
import org.junit.Test;

public class TwimgTest {
    @Test
    public void resolveMediaUrl() throws Exception {
        Twimg twimg = new Twimg("https://pbs.twimg.com/media/BcGV_1TCMAAc-qt.png");
        Assert.assertEquals("https://pbs.twimg.com/media/BcGV_1TCMAAc-qt.png:orig", twimg.resolveMediaUrl());
    }

    @Test
    public void resolveThumbnailUrl() throws Exception {
        Twimg twimg = new Twimg("https://pbs.twimg.com/media/BcGV_1TCMAAc-qt.png");
        Assert.assertEquals("https://pbs.twimg.com/media/BcGV_1TCMAAc-qt.png:thumb", twimg.resolveThumbnailUrl());
    }

}