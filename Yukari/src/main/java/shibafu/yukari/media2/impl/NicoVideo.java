package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        URL url = new URL("https://api.ce.nicovideo.jp/nicoapi/v1/video.info?__format=json&v=" + matcher.group(1));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        try {
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
        } finally {
            br.close();
            conn.disconnect();
        }

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK && conn.getContentType().contains("application/json")) {
            NicoVideoResponseRoot response = new Gson().fromJson(sb.toString(), NicoVideoResponseRoot.class);
            if ("ok".equals(response.nicovideoVideoResponse.status)) {
                return response.nicovideoVideoResponse.video.thumbnailUrl;
            }
        }
        throw new IOException("Invalid Response : " + getBrowseUrl());
    }

    @Override
    public boolean canPreview() {
        return false;
    }

    private static class NicoVideoResponseRoot {
        @SerializedName("nicovideo_video_response")
        public NicoVideoVideoResponse nicovideoVideoResponse;
    }

    private static class NicoVideoVideoResponse {
        @SerializedName("video")
        public NicoVideoVideoInfo video;
        @SerializedName("@status")
        public String status;
    }

    private static class NicoVideoVideoInfo {
        @SerializedName("thumbnail_url")
        public String thumbnailUrl;
    }
}
