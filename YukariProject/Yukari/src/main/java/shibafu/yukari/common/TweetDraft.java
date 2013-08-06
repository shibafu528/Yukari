package shibafu.yukari.common;

import android.net.Uri;

import java.io.Serializable;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/07.
 */
public class TweetDraft implements Serializable{
    public AuthUserRecord user;
    public String text;
    public Status from;
    public Uri[] attachMedia;
}
