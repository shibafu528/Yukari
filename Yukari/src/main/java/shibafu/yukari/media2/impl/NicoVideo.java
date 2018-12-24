package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NicoVideo extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("http://(?:(?:www|sp)\\.nicovideo\\.jp/watch|nico\\.ms)/[sn][mo]([1-9]\\d*)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public NicoVideo(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return ("http://tn.smilevideo.jp/smile?i=" + matcher.group(1));
        }
        return null;
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
