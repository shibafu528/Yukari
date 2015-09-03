package shibafu.yukari.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by shibafu on 15/06/24.
 */
public class StringUtilTest {

    @Test
    public void testCompressText() throws Exception {
        Assert.assertEquals("【悲報】", StringUtil.compressText("【悲報】\n【悲報】\n【悲報】\n【悲報】"));
        Assert.assertEquals("【悲報】", StringUtil.compressText("【悲報】  \n【悲報】\n【悲報】 \n【悲報】      "));
        Assert.assertNull(StringUtil.compressText("【悲報】\n【悲報】\n【悲報】"));
        Assert.assertEquals("[ﾟдﾟ]", StringUtil.compressText("[ﾟдﾟ]\n[ﾟдﾟ]\n[ﾟдﾟ]\n[ﾟдﾟ]"));
        Assert.assertNull(StringUtil.compressText("[ﾟдﾟ]\n[ﾟдﾟ]\n[ﾟдﾟ]"));
        Assert.assertEquals("↓", StringUtil.compressText("わかる\n↓\nウケる\n↓\n神\n↓\nわかる\n↓\nウケる\n↓\n神"));
    }
}