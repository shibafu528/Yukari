package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sunoho extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("http://(?:gyazo\\.)?sunoho\\.com(?:/p)?/i/([a-f0-9]+)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Sunoho(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://gyazo.sunoho.com/i/" + matcher.group(1) + ".png";
        }
        return null;
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
