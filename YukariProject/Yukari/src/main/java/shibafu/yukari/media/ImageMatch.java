package shibafu.yukari.media;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* Created by Shibafu on 13/12/30.
*/
public class ImageMatch implements Cloneable{
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

        String url = mFullPagePattern;
        for (int i = 0; i < fragment.length; i++) {
            url = url.replace("%" + (i+1), fragment[i]);
        }
        return url;
    }
}
