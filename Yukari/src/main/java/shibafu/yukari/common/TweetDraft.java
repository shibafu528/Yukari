package shibafu.yukari.common;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.GeoLocation;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TweetDraft that = (TweetDraft) o;

        if (dateTime != that.dateTime) return false;
        if (inReplyTo != that.inReplyTo) return false;
        if (isQuoted != that.isQuoted) return false;
        if (useGeoLocation != that.useGeoLocation) return false;
        if (Double.compare(that.geoLatitude, geoLatitude) != 0) return false;
        if (Double.compare(that.geoLongitude, geoLongitude) != 0) return false;
        if (isPossiblySensitive != that.isPossiblySensitive) return false;
        if (isDirectMessage != that.isDirectMessage) return false;
        if (isFailedDelivery != that.isFailedDelivery) return false;
        if (!writers.equals(that.writers)) return false;
        if (!text.equals(that.text)) return false;
        if (!attachedPictures.equals(that.attachedPictures)) return false;
        return !(messageTarget != null ? !messageTarget.equals(that.messageTarget) : that.messageTarget != null);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = writers.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + (int) (dateTime ^ (dateTime >>> 32));
        result = 31 * result + (int) (inReplyTo ^ (inReplyTo >>> 32));
        result = 31 * result + (isQuoted ? 1 : 0);
        result = 31 * result + attachedPictures.hashCode();
        result = 31 * result + (useGeoLocation ? 1 : 0);
        temp = Double.doubleToLongBits(geoLatitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(geoLongitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (isPossiblySensitive ? 1 : 0);
        result = 31 * result + (isDirectMessage ? 1 : 0);
        result = 31 * result + (isFailedDelivery ? 1 : 0);
        result = 31 * result + (messageTarget != null ? messageTarget.hashCode() : 0);
        return result;
    }

    @Override
    public Object clone() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {
        private ArrayList<AuthUserRecord> writers = new ArrayList<>();
        private String text;
        private long dateTime = System.currentTimeMillis();
        private long inReplyTo = -1;
        private boolean isQuoted;
        private transient ArrayList<Uri> attachedPictures = new ArrayList<>();
        private boolean useGeoLocation;
        private double geoLatitude;
        private double geoLongitude;
        private boolean isPossiblySensitive;
        private boolean isDirectMessage;
        private boolean isFailedDelivery;
        private String messageTarget;

        public Builder setWriters(ArrayList<AuthUserRecord> writers) {
            this.writers = writers;
            return this;
        }

        public Builder addWriter(AuthUserRecord writer) {
            this.writers.add(writer);
            return this;
        }

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setDateTime(long dateTime) {
            this.dateTime = dateTime;
            return this;
        }

        public Builder setInReplyTo(long inReplyTo) {
            this.inReplyTo = inReplyTo;
            return this;
        }

        public Builder setQuoted(boolean isQuoted) {
            this.isQuoted = isQuoted;
            return this;
        }

        public Builder setAttachedPictures(ArrayList<Uri> attachedPictures) {
            this.attachedPictures = attachedPictures;
            return this;
        }

        public Builder addAttachedPicture(Uri attachedPicture) {
            this.attachedPictures.add(attachedPicture);
            return this;
        }

        public Builder setUseGeoLocation(boolean useGeoLocation) {
            this.useGeoLocation = useGeoLocation;
            return this;
        }

        public Builder setGeoLocation(double latitude, double longitude) {
            this.geoLatitude = latitude;
            this.geoLongitude = longitude;
            return this;
        }

        public Builder setPossiblySensitive(boolean isPossiblySensitive) {
            this.isPossiblySensitive = isPossiblySensitive;
            return this;
        }

        public Builder setDirectMessage(boolean isDirectMessage) {
            this.isDirectMessage = isDirectMessage;
            return this;
        }

        public Builder setFailedDelivery(boolean isFailedDelivery) {
            this.isFailedDelivery = isFailedDelivery;
            return this;
        }

        public Builder setMessageTarget(String messageTarget) {
            this.messageTarget = messageTarget;
            return this;
        }

        public TweetDraft build() {
            if (isDirectMessage) {
                return new TweetDraft(writers, text, dateTime, inReplyTo, messageTarget, isQuoted, attachedPictures, useGeoLocation, geoLatitude, geoLongitude, isPossiblySensitive, isFailedDelivery);
            } else {
                return new TweetDraft(writers, text, dateTime, inReplyTo, isQuoted, attachedPictures, useGeoLocation, geoLatitude, geoLongitude, isPossiblySensitive, isFailedDelivery);
            }
        }
    }
}
