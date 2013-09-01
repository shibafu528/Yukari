package shibafu.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TweetImageUrl {
	
	private static final ImageMatch[] pattern = {
		new ImageMatch("http://twitpic\\.com/([a-zA-Z0-9]+)", "http://twitpic.com/show/full/%1"),
		new ImageMatch("http://yfrog\\.com/([a-zA-Z0-9]+)", "http://yfrog.com/%1:medium"),
		new ImageMatch("http://p\\.twipple\\.jp/([a-zA-Z0-9]+)", "http://p.twpl.jp/show/orig/%1")
	};
	
	private static class ImageMatch implements Cloneable{
		private Pattern mPage;
		private String mFullPagePattern;
		
		public ImageMatch(String pageRegex, String fullPagePattern) {
			mPage = Pattern.compile(pageRegex);
			mFullPagePattern = fullPagePattern;
		}
		
		public String getFullPageUrl(String pageUrl) {
			String[] fragment;
			Matcher m = mPage.matcher(pageUrl);
			if (m.find()) {
				List<String> l = new ArrayList<String>();
				for (int i = 1; i <= m.groupCount(); i++) {
					l.add(m.group(i));
				}
				fragment = l.toArray(new String[l.size()]);
			}
			else return null;
			
			String url = new String(mFullPagePattern);
			for (int i = 0; i < fragment.length; i++) {
				url = url.replace("%" + (i+1), fragment[i]);
			}
			return url;
		}
	}
	
	public static String getFullImageUrl(String pageUrl) {
		for (ImageMatch match : pattern) {
			String url = match.getFullPageUrl(pageUrl);
			if (url != null)
				return url;
		}
		return null;
	}
	
}
