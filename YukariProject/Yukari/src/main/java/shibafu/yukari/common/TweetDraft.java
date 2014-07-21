package shibafu.yukari.common;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.GeoLocation;

/**
 * Created by Shibafu on 13/08/07.
 */
public class TweetDraft implements Serializable{

    private ArrayList<AuthUserRecord> writers = new ArrayList<>();
    private String text;
    private long dateTime;
    private long inReplyTo;
    private boolean isQuoted;
    private transient ArrayList<Uri> attachedPictures = new ArrayList<>();
    private boolean useGeoLocation;
    private double geoLatitude;
    private double geoLongitude;
    private boolean isPossiblySensitive;
    private boolean isDirectMessage;
    private boolean isFailedDelivery;
    private String messageTarget;

    public TweetDraft(ArrayList<AuthUserRecord> writers, String text, long dateTime, long inReplyTo,
                      boolean isQuoted, List<Uri> attachedPictures,
                      boolean useGeoLocation,
                      double geoLatitude, double geoLongitude,
                      boolean isPossiblySensitive, boolean isFailedDelivery) {
        this.writers = writers;
        this.text = text;
        this.dateTime = dateTime;
        this.inReplyTo = inReplyTo;
        this.isQuoted = isQuoted;
        if (attachedPictures != null) {
            this.attachedPictures.addAll(attachedPictures);
        }
        this.useGeoLocation = useGeoLocation;
        this.geoLatitude = geoLatitude;
        this.geoLongitude = geoLongitude;
        this.isPossiblySensitive = isPossiblySensitive;
        this.isDirectMessage = false;
        this.isFailedDelivery = isFailedDelivery;
        this.messageTarget = "";
    }

    public TweetDraft(ArrayList<AuthUserRecord> writers, String text, long dateTime,
                      long inReplyTo, String messageTarget,
                      boolean isQuoted, List<Uri> attachedPictures,
                      boolean useGeoLocation,
                      double geoLatitude, double geoLongitude,
                      boolean isPossiblySensitive,
                      boolean isFailedDelivery) {
        this.writers = writers;
        this.text = text;
        this.dateTime = dateTime;
        this.inReplyTo = inReplyTo;
        this.isQuoted = isQuoted;
        if (attachedPictures != null) {
            this.attachedPictures.addAll(attachedPictures);
        }
        this.useGeoLocation = useGeoLocation;
        this.geoLatitude = geoLatitude;
        this.geoLongitude = geoLongitude;
        this.isPossiblySensitive = isPossiblySensitive;
        this.isDirectMessage = true;
        this.isFailedDelivery = isFailedDelivery;
        this.messageTarget = messageTarget;
    }

    public TweetDraft(Cursor cursor) {
        text = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_TEXT));
        dateTime = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_DATETIME));
        inReplyTo = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IN_REPLY_TO));
        isQuoted = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_QUOTED)) == 1;
        {
            String attachedPictureString = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE));
            if (!TextUtils.isEmpty(attachedPictureString)) {
                for (String att : attachedPictureString.split("\\|")) {
                    attachedPictures.add(Uri.parse(att));
                }
            }
        }
        useGeoLocation = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION)) == 1;
        geoLatitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LATITUDE));
        geoLongitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE));
        isPossiblySensitive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE)) == 1;
        isDirectMessage = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE)) == 1;
        isFailedDelivery = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY)) == 1;
        messageTarget = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_MESSAGE_TARGET));
    }

    public ContentValues[] getContentValuesArray() {
        ContentValues[] valuesArray = new ContentValues[writers.size()];
        for (int i = 0; i < writers.size(); ++i) {
            ContentValues values = new ContentValues();
            values.put(CentralDatabase.COL_DRAFTS_WRITER_ID, writers.get(i).NumericId);
            values.put(CentralDatabase.COL_DRAFTS_DATETIME, dateTime);
            values.put(CentralDatabase.COL_DRAFTS_TEXT, text);
            values.put(CentralDatabase.COL_DRAFTS_IN_REPLY_TO, inReplyTo);
            values.put(CentralDatabase.COL_DRAFTS_IS_QUOTED, isQuoted);
            if (!attachedPictures.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Uri u : attachedPictures) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(u.toString());
                }
                values.put(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE, sb.toString());
            }
            values.put(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION, useGeoLocation);
            values.put(CentralDatabase.COL_DRAFTS_GEO_LATITUDE, geoLatitude);
            values.put(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE, geoLongitude);
            values.put(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE, isPossiblySensitive);
            values.put(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE, isDirectMessage);
            values.put(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY, isFailedDelivery);
            values.put(CentralDatabase.COL_DRAFTS_MESSAGE_TARGET, messageTarget);
            valuesArray[i] = values;
        }
        return valuesArray;
    }

    public void updateFields(ArrayList<AuthUserRecord> writers, String text, long inReplyTo,
                             boolean isQuoted, List<Uri> attachedPictures,
                             boolean useGeoLocation,
                             double geoLatitude, double geoLongitude,
                             boolean isPossiblySensitive) {
        this.writers = writers;
        this.text = text;
        this.inReplyTo = inReplyTo;
        this.isQuoted = isQuoted;
        if (attachedPictures != null) {
            this.attachedPictures.clear();
            this.attachedPictures.addAll(attachedPictures);
        }
        this.useGeoLocation = useGeoLocation;
        this.geoLatitude = geoLatitude;
        this.geoLongitude = geoLongitude;
        this.isPossiblySensitive = isPossiblySensitive;
        this.isDirectMessage = false;
        this.messageTarget = "";
    }

    public void updateFields(ArrayList<AuthUserRecord> writers, String text,
                             long inReplyTo, String messageTarget,
                             boolean isQuoted, List<Uri> attachedPictures,
                             boolean useGeoLocation,
                             double geoLatitude, double geoLongitude,
                             boolean isPossiblySensitive) {
        this.writers = writers;
        this.text = text;
        this.inReplyTo = inReplyTo;
        this.isQuoted = isQuoted;
        if (attachedPictures != null) {
            this.attachedPictures.clear();
            this.attachedPictures.addAll(attachedPictures);
        }
        this.useGeoLocation = useGeoLocation;
        this.geoLatitude = geoLatitude;
        this.geoLongitude = geoLongitude;
        this.isPossiblySensitive = isPossiblySensitive;
        this.isDirectMessage = true;
        this.messageTarget = messageTarget;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(long inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

    public boolean isQuoted() {
        return isQuoted;
    }

    public void setQuoted(boolean isQuoted) {
        this.isQuoted = isQuoted;
    }

    public List<Uri> getAttachedPictures() {
        return attachedPictures;
    }

    public ArrayList<String> getStringAttachedPictures() {
        ArrayList<String> list = new ArrayList<>();
        for (Uri u : attachedPictures) {
            list.add(u.toString());
        }
        return list;
    }

    public double getGeoLatitude() {
        return geoLatitude;
    }

    public void setGeoLatitude(double geoLatitude) {
        this.geoLatitude = geoLatitude;
    }

    public double getGeoLongitude() {
        return geoLongitude;
    }

    public void setGeoLongitude(double geoLongitude) {
        this.geoLongitude = geoLongitude;
    }

    public boolean isPossiblySensitive() {
        return isPossiblySensitive;
    }

    public void setPossiblySensitive(boolean isPossiblySensitive) {
        this.isPossiblySensitive = isPossiblySensitive;
    }

    public boolean isDirectMessage() {
        return isDirectMessage;
    }

    public void setDirectMessage(boolean isDirectMessage) {
        this.isDirectMessage = isDirectMessage;
    }

    public boolean isFailedDelivery() {
        return isFailedDelivery;
    }

    public void setFailedDelivery(boolean isFailedDelivery) {
        this.isFailedDelivery = isFailedDelivery;
    }

    public long getDateTime() {
        return dateTime;
    }

    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
    }

    public boolean isUseGeoLocation() {
        return useGeoLocation;
    }

    public void setUseGeoLocation(boolean useGeoLocation) {
        this.useGeoLocation = useGeoLocation;
    }

    public ArrayList<AuthUserRecord> getWriters() {
        return writers;
    }

    public void setWriters(ArrayList<AuthUserRecord> writers) {
        this.writers = writers;
    }

    public void addWriter(AuthUserRecord user) {
        writers.add(user);
    }

    public String getMessageTarget() {
        return messageTarget;
    }

    public void setMessageTarget(String messageTarget) {
        this.messageTarget = messageTarget;
    }

    public Intent getTweetIntent(Context context) {
        Intent intent = new Intent(context, TweetActivity.class);
        intent.putExtra(TweetActivity.EXTRA_TEXT, getText());
        intent.putExtra(TweetActivity.EXTRA_MEDIA, getStringAttachedPictures());
        intent.putExtra(TweetActivity.EXTRA_WRITERS, getWriters());
        if (isDirectMessage()) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
            intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, getInReplyTo());
            intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, getMessageTarget());
        }
        else if (getInReplyTo() > -1) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
            intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, getInReplyTo());
        }
        else {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_TWEET);
        }
        if (isUseGeoLocation()) {
            intent.putExtra(TweetActivity.EXTRA_GEO_LOCATION, new GeoLocation(getGeoLatitude(), getGeoLongitude()));
        }
        intent.putExtra(TweetActivity.EXTRA_DRAFT, this);
        return intent;
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();

        stream.writeInt(attachedPictures.size());
        for (Uri u : attachedPictures) {
            stream.writeUTF(u.toString());
        }
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        int attached = stream.readInt();
        attachedPictures = new ArrayList<>();
        for (int i = 0; i < attached; ++i) {
            attachedPictures.add(Uri.parse(stream.readUTF()));
        }
    }
}
