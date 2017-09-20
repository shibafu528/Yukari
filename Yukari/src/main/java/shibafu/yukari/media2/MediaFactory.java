package shibafu.yukari.media2;

import shibafu.yukari.media2.impl.D250g2;
import shibafu.yukari.media2.impl.Gyazo;
import shibafu.yukari.media2.impl.Imgly;
import shibafu.yukari.media2.impl.Irasutoya;
import shibafu.yukari.media2.impl.NicoSeiga;
import shibafu.yukari.media2.impl.NicoVideo;
import shibafu.yukari.media2.impl.Nijie;
import shibafu.yukari.media2.impl.Photozou;
import shibafu.yukari.media2.impl.Pixiv;
import shibafu.yukari.media2.impl.RouterCake;
import shibafu.yukari.media2.impl.SimplePicture;
import shibafu.yukari.media2.impl.SixHundredEUR;
import shibafu.yukari.media2.impl.Sunoho;
import shibafu.yukari.media2.impl.Totori;
import shibafu.yukari.media2.impl.Twimg;
import shibafu.yukari.media2.impl.Twipple;
import shibafu.yukari.media2.impl.Twitpic;
import shibafu.yukari.media2.impl.TwitterVideo;
import shibafu.yukari.media2.impl.Vine;
import shibafu.yukari.media2.impl.XVideos;
import shibafu.yukari.media2.impl.YouTube;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MediaFactory {
    private static Map<String, Media> instanceQueue = Collections.synchronizedMap(new LinkedHashMap<String, Media>() {
        @Override
        protected boolean removeEldestEntry(Entry eldest) {
            return size() > 32;
        }
    });

    public static Media newInstance(String browseUrl) {
        if (browseUrl == null) return null;

        Media media = instanceQueue.get(browseUrl);

        if (media == null) {
            if (browseUrl.contains("pbs.twimg.com/media/")) {
                media = new Twimg(browseUrl);
            } else if (browseUrl.contains("pbs.twimg.com/profile_")) {
                media = new SimplePicture(browseUrl);
            } else if (browseUrl.contains("pbs.twimg.com/tweet_video/") || browseUrl.contains("video.twimg.com")) {
                media = new TwitterVideo(browseUrl);
            } else if (browseUrl.contains("twitpic.com/d250g2") || browseUrl.contains("d250g2.com")) {
                media = new D250g2(browseUrl);
            } else if (browseUrl.contains("gyazo.com")) {
                media = new Gyazo(browseUrl);
            } else if (browseUrl.contains("img.ly/")) {
                if (browseUrl.contains("img.ly/show/")) {
                    media = new SimplePicture(browseUrl);
                } else {
                    media = new Imgly(browseUrl);
                }
            } else if (browseUrl.contains("nico.ms/im") || browseUrl.contains("seiga.nicovideo.jp/seiga/im") || browseUrl.contains("sp.seiga.nicovideo.jp/seiga/#!/im")) {
                media = new NicoSeiga(browseUrl);
            } else if ((browseUrl.contains("www.nicovideo.jp/watch") || browseUrl.contains("nico.ms/") || browseUrl.contains("sp.nicovideo.jp/watch"))
                    && (browseUrl.contains("/sm") || browseUrl.contains("/nm"))) {
                media = new NicoVideo(browseUrl);
            } else if (browseUrl.contains("nijie.info/view.php")) {
                media = new Nijie(browseUrl);
            } else if (browseUrl.contains("photozou.jp")) {
                media = new Photozou(browseUrl);
            } else if (browseUrl.contains("pixiv.net/member_illust.php")) {
                media = new Pixiv(browseUrl);
            } else if (RouterCake.ORIGIN_URL.equals(browseUrl)) {
                media = new RouterCake(browseUrl);
            } else if (browseUrl.contains("600eur.gochiusa.net")) {
                media = new SixHundredEUR(browseUrl);
            } else if (browseUrl.contains("sunoho.com/p/i") || browseUrl.contains("gyazo.sunoho.com/i")) {
                media = new Sunoho(browseUrl);
            } else if ((browseUrl.endsWith("/") ? browseUrl.substring(0, browseUrl.length() - 1) : browseUrl).equals("http://totori.dip.jp")) {
                media = new Totori(browseUrl);
            } else if (browseUrl.contains("p.twipple.jp")) {
                media = new Twipple(browseUrl);
            } else if (browseUrl.contains("twitpic.com")) {
                media = new Twitpic(browseUrl);
            } else if (browseUrl.contains("/vine.co/v/")) {
                media = new Vine(browseUrl);
            } else if (browseUrl.contains("xvideos.com")) {
                media = new XVideos(browseUrl);
            } else if (browseUrl.contains("www.youtube.com/watch?") || browseUrl.contains("youtu.be/")) {
                media = new YouTube(browseUrl);
            } else if (Irasutoya.URL_PATTERN.matcher(browseUrl).find()) {
                media = new Irasutoya(browseUrl);
            } else {
                String lc = browseUrl.toLowerCase();
                if (lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") || lc.endsWith(".gif")) {
                    media = new SimplePicture(browseUrl);
                }
            }
        }

        instanceQueue.put(browseUrl, media);

        return media;
    }
}
