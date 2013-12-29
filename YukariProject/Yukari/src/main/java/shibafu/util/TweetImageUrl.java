package shibafu.util;

import shibafu.yukari.media.ImageMatch;

@Deprecated
public class TweetImageUrl {
	
	private static final ImageMatch[] pattern = {
		new ImageMatch("http://twitpic\\.com/([a-zA-Z0-9]+)", "http://twitpic.com/show/full/%1"),
		new ImageMatch("http://yfrog\\.com/([a-zA-Z0-9]+)", "http://yfrog.com/%1:medium"),
		new ImageMatch("http://p\\.twipple\\.jp/([a-zA-Z0-9]+)", "http://p.twpl.jp/show/orig/%1")
	};

    @Deprecated
    public static String getFullImageUrl(String pageUrl) {
		for (ImageMatch match : pattern) {
			String url = match.getFullPageUrl(pageUrl);
			if (url != null)
				return url;
		}
		return null;
	}
	
}
