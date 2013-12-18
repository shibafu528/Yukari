package shibafu.yukari.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.IOException;

import twitter4j.User;

/**
 * Created by Shibafu on 13/08/15.
 */
public class FriendsDBHelper {

    private static final String DB_NAME = "friends.db";
    private static final int DB_VER = 1;

    public static final String TABLE_NAME = "friends";
    public static final String COL_ID = "_id";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_SCREEN_NAME = "screen_name";
    public static final String COL_NAME = "name";
    public static final String COL_PICTURE_URL = "pic_url";

    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "create table " + TABLE_NAME + " (" +
                    COL_ID + " integer primary key autoincrement," +
                    COL_USER_ID + " integer not null," +
                    COL_SCREEN_NAME + " text not null," +
                    COL_NAME + " text not null," +
                    COL_PICTURE_URL + " text)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("drop table if exists " + TABLE_NAME);
            onCreate(db);
        }
    }

    public FriendsDBHelper(Context context) {
        this.context = context;
        helper = new DatabaseHelper(this.context);
    }

    public FriendsDBHelper open() {
        db = helper.getWritableDatabase();
        return this;
    }

    public void close() {
        db.close();
    }

    public void beginTransaction() {
        db.beginTransaction();
    }

    public void successTransaction() {
        db.setTransactionSuccessful();
    }

    public void endTransaction() {
        db.endTransaction();
    }

    public boolean deleteAll() {
        return db.delete(TABLE_NAME, null, null) > 0;
    }

    public Cursor getAllFriends() {
        return db.query(TABLE_NAME, null, null, null, null, null, COL_SCREEN_NAME + " asc");
    }

    public Cursor getFriends(String where, String[] whereClause) {
        return db.query(TABLE_NAME, null, where, whereClause, null, null, COL_SCREEN_NAME + " asc");
    }

    public Cursor getFriend(long id) {
        return db.query(TABLE_NAME, null, COL_USER_ID + "=" + id, null, null, null, null);
    }

    /**
     * ユーザーデータを保存します<br/>
     * Http通信を行う可能性があるため非同期で呼べ
     * @param user
     * @throws IOException
     */
    public void saveRecord(User user) throws IOException {
        Cursor c = getFriend(user.getId());
        boolean exist = c.getCount() > 0;
        c.close();

        ContentValues values = new ContentValues();
        values.put(COL_NAME, user.getName());
        values.put(COL_SCREEN_NAME, user.getScreenName());
        values.put(COL_USER_ID, user.getId());
        values.put(COL_PICTURE_URL, user.getProfileImageURL());

        if (exist) {
            db.update(TABLE_NAME, values, COL_USER_ID + "=" + user.getId(), null);
        }
        else {
            db.insertOrThrow(TABLE_NAME, null, values);
        }
    }
}
