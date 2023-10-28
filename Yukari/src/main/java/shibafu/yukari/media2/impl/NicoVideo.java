package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.media2.MemoizeMedia;

public class NicoVideo extends MemoizeMedia {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:(?:www|sp)\\.nicovideo\\.jp/watch|nico\\.ms)/([sn][mo][1-9]\\d*)");

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
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid URL : " + getBrowseUrl());
        }
        return Jsoup.connect("https://www.nicovideo.jp/watch/" + matcher.group(1))
                .timeout(10000)
                .get()
                .select("meta[property=og:image]")
                .attr("content");
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
