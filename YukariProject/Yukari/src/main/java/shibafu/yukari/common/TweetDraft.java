package shibafu.yukari.common;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

import shibafu.common.SerialRecord;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/07.
 */
public class TweetDraft implements Serializable{
    private static final String FILE_NAME = "drafts";

    public AuthUserRecord user;
    public String text;
    public Status from;
    public String[] attachMedia;
    public Date time;

    public TweetDraft(AuthUserRecord user, String text, Status from, String[] attachMedia) {
        this.user = user;
        this.text = text;
        this.from = from;
        this.attachMedia = attachMedia;
        time = new Date(System.currentTimeMillis());
    }

    public static List<TweetDraft> loadDrafts(Context context){
        try {
            return SerialRecord.loadSerialClassRecords(new File(context.getFilesDir(), FILE_NAME));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void saveDrafts(Context context, List<TweetDraft> drafts) throws IOException {
        SerialRecord.writeSerialClassRecords(context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE), drafts);
    }
}
