package shibafu.yukari.media2.impl;

import org.junit.Assert;
import org.junit.Test;
import shibafu.yukari.media2.Media;

public class PixivTest {
    @Test
    public void resolveMedia() throws Exception {
        Pixiv pixiv = new Pixiv("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=20");
        Media.ResolveInfo resolveInfo = pixiv.resolveMedia();
        Assert.assertNotNull(resolveInfo);
        Assert.assertNotNull(resolveInfo.getStream());

        byte[] buffer = new byte[15];
        int read = resolveInfo.getStream().read(buffer, 0, buffer.length);
        Assert.assertEquals(buffer.length, read);

        String dtd = new String(buffer);
        Assert.assertEquals("<!doctype html>", dtd.toLowerCase());
    }

    @Test
    public void resolveThumbnail() throws Exception {
        Pixiv pixiv = new Pixiv("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=20");
        Media.ResolveInfo resolveInfo = pixiv.resolveThumbnail();
        Assert.assertNotNull(resolveInfo);
        Assert.assertNotNull(resolveInfo.getStream());

        byte[] header = new byte[2];
        int read = resolveInfo.getStream().read(header, 0, header.length);
        Assert.assertEquals(header.length, read);

        Assert.assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8}, header);
    }

}