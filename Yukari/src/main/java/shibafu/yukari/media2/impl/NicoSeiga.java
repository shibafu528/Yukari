package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NicoSeiga extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:(?:sp\\.)?seiga\\.nicovideo\\.jp/seiga(?:/#!)?|nico\\.ms)/im(\\d+)");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public NicoSeiga(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://lohas.nicoseiga.jp/thumb/" + matcher.group(1) + "l?";
        }
        return null;
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (matcher.find()) {
            return "http://lohas.nicoseiga.jp/thumb/" + matcher.group(1) + "i?";
        }
        return null;
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
