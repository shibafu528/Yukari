package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;
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

        Request request = new Request.Builder()
                .url("https://ext.nicovideo.jp/api/getthumbinfo/" + matcher.group(1))
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            try {
                XmlPullParserFactory xppFactory = XmlPullParserFactory.newInstance();
                xppFactory.setValidating(false);
                XmlPullParser xpp = xppFactory.newPullParser();
                xpp.setInput(response.body().charStream());

                int eventType = xpp.getEventType();
                boolean inThumbnailUrl = false;

                // XPath: /nicovideo_thumb_response/thumb/thumbnail_url を取得する
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if ("nicovideo_thumb_response".equals(xpp.getName())) {
                            // ルートノードには取得に成功したかどうかが書かれているので、一応見ておく
                            // <nicovideo_thumb_response status="ok"> or <nicovideo_thumb_response status="fail">
                            String status = xpp.getAttributeValue("", "status");
                            if (!"ok".equals(status)) {
                                return null;
                            }
                        } else if ("thumbnail_url".equals(xpp.getName())) {
                            // 他に名前が被っているノードがないので親子関係は気にせず、この名前のノードが来たら中のテキスト要素を返す
                            inThumbnailUrl = true;
                        }
                    } else if (eventType == XmlPullParser.TEXT && inThumbnailUrl) {
                        // <thumbnail_url>https://nicovideo.cdn.nimg.jp/...</thumbnail_url>
                        //                 ↑ これ
                        return xpp.getText();
                    }

                    eventType = xpp.next();
                }
            } catch (XmlPullParserException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
