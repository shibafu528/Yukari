package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTube extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://(?:www\\.youtube\\.com/watch\\?.*v=|youtu\\.be/)([\\w-]+)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public YouTube(@NonNull String browseUrl) {
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
            return "http://i.ytimg.com/vi/" + matcher.group(1) + "/default.jpg";
        }
        return null;
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
