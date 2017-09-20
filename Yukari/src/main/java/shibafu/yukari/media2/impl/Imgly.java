package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Imgly extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("http://img\\.ly/([a-zA-Z0-9]+)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Imgly(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://img.ly/show/full/" + matcher.group(1);
        }
        return null;
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://img.ly/show/thumb/" + matcher.group(1);
        }
        return null;
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
