package shibafu.yukari.common;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import shibafu.common.SerialRecord;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBRecord;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/07.
 */
public class TweetDraft implements Serializable{

    private int id = -1;
    private ArrayList<AuthUserRecord> writers = new ArrayList<AuthUserRecord>();
    private String text;
    private long dateTime;
    private long inReplyTo;
    private boolean isQuoted;
    private Uri attachedPicture;
    private boolean useGeoLocation;
    private double geoLatitude;
    private double geoLongitude;
    private boolean isPossiblySensitive;
    private boolean isDirectMessage;
    private boolean isFailedDelivery;

    public TweetDraft(ArrayList<AuthUserRecord> writers, String text, long dateTime, long inReplyTo,
                      boolean isQuoted, Uri attachedPicture,
                      boolean useGeoLocation,
                      double geoLatitude, double geoLongitude,
                      boolean isPossiblySensitive, boolean isDirectMessage, boolean isFailedDelivery) {
        this.writers = writers;
        this.text = text;
        this.dateTime = dateTime;
        this.inReplyTo = inReplyTo;
        this.isQuoted = isQuoted;
        this.attachedPicture = attachedPicture;
        this.useGeoLocation = useGeoLocation;
        this.geoLatitude = geoLatitude;
        this.geoLongitude = geoLongitude;
        this.isPossiblySensitive = isPossiblySensitive;
        this.isDirectMessage = isDirectMessage;
        this.isFailedDelivery = isFailedDelivery;
    }

    public TweetDraft(Cursor cursor) {
        id = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_ID));
        text = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_TEXT));
        dateTime = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_DATETIME));
        inReplyTo = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IN_REPLY_TO));
        isQuoted = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_QUOTED)) == 1;
        String attachedPictureString = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE));
        attachedPicture = (attachedPictureString==null || attachedPictureString.equals(""))? null : Uri.parse(attachedPictureString);
        useGeoLocation = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION)) == 1;
        geoLatitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LATITUDE));
        geoLongitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE));
        isPossiblySensitive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE)) == 1;
        isDirectMessage = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE)) == 1;
        isFailedDelivery = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY)) == 1;
    }

    public ContentValues[] getContentValuesArray() {
        ContentValues[] valuesArray = new ContentValues[writers.size()];
        for (int i = 0; i < writers.size(); ++i) {
            ContentValues values = new ContentValues();
            if (id > -1) values.put(CentralDatabase.COL_DRAFTS_ID, id);
            values.put(CentralDatabase.COL_DRAFTS_WRITER_ID, writers.get(i).NumericId);
            values.put(CentralDatabase.COL_DRAFTS_DATETIME, dateTime);
            values.put(CentralDatabase.COL_DRAFTS_TEXT, text);
            values.put(CentralDatabase.COL_DRAFTS_IN_REPLY_TO, inReplyTo);
            values.put(CentralDatabase.COL_DRAFTS_IS_QUOTED, isQuoted);
            if (attachedPicture != null) values.put(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE, attachedPicture.toString());
            values.put(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION, useGeoLocation);
            values.put(CentralDatabase.COL_DRAFTS_GEO_LATITUDE, geoLatitude);
            values.put(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE, geoLongitude);
            values.put(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE, isPossiblySensitive);
            values.put(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE, isDirectMessage);
            values.put(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY, isFailedDelivery);
            valuesArray[i] = values;
        }
        return valuesArray;
    }

    public int getId() {
        return id;
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

    public Uri getAttachedPicture() {
        return attachedPicture;
    }

    public void setAttachedPicture(Uri attachedPicture) {
        this.attachedPicture = attachedPicture;
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
}
