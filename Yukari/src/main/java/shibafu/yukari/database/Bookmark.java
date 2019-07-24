package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

/**
 * Created by shibafu on 14/11/02.
 */
@DBTable(CentralDatabase.TABLE_BOOKMARKS)
public class Bookmark extends PreformedStatus implements DBRecord{
    private Date saveDate;

    public Bookmark(PreformedStatus status) {
        super(status);
        this.saveDate = new Date();
    }

    public Bookmark(Cursor cursor) {
        super(byteArrayToStatus(cursor.getBlob(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_BLOB))), new AuthUserRecord(cursor));
        this.saveDate = new Date(cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_SAVE_DATE)));
    }

    public Date getSaveDate() {
        return saveDate;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(CentralDatabase.COL_BOOKMARKS_ID, getId());
        values.put(CentralDatabase.COL_BOOKMARKS_RECEIVER_ID, getRepresentUser().InternalId);
        values.put(CentralDatabase.COL_BOOKMARKS_SAVE_DATE, saveDate.getTime());
        values.put(CentralDatabase.COL_BOOKMARKS_BLOB, statusToByteArray());
        return values;
    }

    private byte[] statusToByteArray() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(getBaseStatus());
            oos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static Status byteArrayToStatus(byte[] byteArray) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (Status) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * インポート・エクスポート時の型としてのみ使用。いずれ廃止して、Bookmarkクラスを参照するべき。
     */
    public static class SerializeEntity {}
}
