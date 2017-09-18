package shibafu.yukari.media2;

public class MediaFactory {

    public static Media newInstance(String browseUrl) {
        Media media = null;

        if (browseUrl.contains("pbs.twimg.com/media/")) {
            media = new Twimg(browseUrl);
        } else if (browseUrl.contains("pixiv.net/member_illust.php")) {
            media = new Pixiv(browseUrl);
        } else if (browseUrl.endsWith(".jpg") || browseUrl.endsWith(".jpeg") || browseUrl.endsWith(".png")) {
            media = new SimplePicture(browseUrl);
        }

        return media;
    }
}
