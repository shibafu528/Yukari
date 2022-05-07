package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Twipple extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("http://p\\.twipple\\.jp/([a-zA-Z0-9]+)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Twipple(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://p.twpl.jp/show/orig/" + matcher.group(1);
        }
        return null;
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://p.twpl.jp/show/thumb/" + matcher.group(1);
        }
        return null;
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
