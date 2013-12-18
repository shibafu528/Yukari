package shibafu.yukari.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Shibafu on 13/12/18.
 */
public class CentralDatabase {

    //DB基本情報
    private static final String DB_FILENAME = "yukari.db";
    private static final int DB_VER = 1;

    //Accountsテーブル
    public static final String TABLE_ACCOUNTS = "Accounts";
    public static final String COL_ACCOUNTS_ID = "_id";
    public static final String COL_ACCOUNTS_ACCESS_TOKEN = "AccessToken";
    public static final String COL_ACCOUNTS_ACCESS_TOKEN_SECRET = "AccessTokenSecret";
    public static final String COL_ACCOUNTS_IS_PRIMARY = "IsPrimary"; //各種操作のメインアカウント、最初に認証した垢がデフォルト
    public static final String COL_ACCOUNTS_IS_ACTIVE  = "IsActive"; //タイムラインのアクティブアカウント
    public static final String COL_ACCOUNTS_IS_WRITER  = "IsWriter"; //ツイートのカレントアカウント、オープン毎にIsPrimaryで初期化する

    //Userテーブル
    // -- Entity類は全て展開してから格納する
    public static final String TABLE_USER = "User";
    public static final String COL_USER_ID = "_id";
    public static final String COL_USER_SCREEN_NAME = "ScreenName";
    public static final String COL_USER_NAME = "Name";
    public static final String COL_USER_DESCRIPTION = "Description";
    public static final String COL_USER_LOCATION = "Location";
    public static final String COL_USER_URL = "Url";
    public static final String COL_USER_PROFILE_IMAGE_URL = "ProfileImageUrl";
    public static final String COL_USER_PROFILE_BANNER_URL = "ProfileBannerUrl";
    public static final String COL_USER_IS_PROTECTED = "IsProtected";
    public static final String COL_USER_IS_VERIFIED = "IsVerified";
    public static final String COL_USER_IS_TRANSLATOR = "IsTranslator";
    public static final String COL_USER_IS_CONTRIBUTORS_ENABLED = "IsContributorsEnabled";
    public static final String COL_USER_IS_GEO_ENABLED = "IsGeoEnabled";
    public static final String COL_USER_STATUSES_COUNT = "StatusesCount";
    public static final String COL_USER_FOLLOWINGS_COUNT = "FollowingsCount";
    public static final String COL_USER_FOLLOWERS_COUNT = "FollowersCount";
    public static final String COL_USER_FAVORITES_COUNT = "FavoritesCount";
    public static final String COL_USER_LISTED_COUNT = "ListedCount";
    public static final String COL_USER_LANGUAGE = "Language";
    public static final String COL_USER_CREATED_AT = "CreatedAt";

    //Draftsテーブル
    public static final String TABLE_DRAFTS = "Drafts";
    public static final String COL_DRAFTS_DRAFTS_ID = "_id";
    public static final String COL_DRAFTS_WRITER_IDS = "WriterIds";
    public static final String COL_DRAFTS_TEXT = "Text";
    public static final String COL_DRAFTS_IN_REPLY_TO = "InReplyTo";
    public static final String COL_DRAFTS_IS_QUOTED = "IsQuoted";
    public static final String COL_DRAFTS_ATTACHED_PICTURE = "AttachedPicture";
    public static final String COL_DRAFTS_GEO_LATITUDE = "GeoLatitude";
    public static final String COL_DRAFTS_GEO_LONGITUDE = "GeoLongitude";
    public static final String COL_DRAFTS_IS_POSSIBLY_SENSITIVE = "IsPossiblySensitive";

    //Bookmarksテーブル

    //インスタンス
    private Context context;
    private CentralDBHelper helper;
    private SQLiteDatabase db;

    private static class CentralDBHelper extends SQLiteOpenHelper {

        public CentralDBHelper(Context context) {
            super(context, DB_FILENAME, null, DB_VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE " + TABLE_ACCOUNTS + " (" +
                    COL_ACCOUNTS_ID + " INTEGER PRIMARY KEY, " +
                    COL_ACCOUNTS_ACCESS_TOKEN + " TEXT, " +
                    COL_ACCOUNTS_ACCESS_TOKEN_SECRET + " TEXT, " +
                    COL_ACCOUNTS_IS_PRIMARY + " INTEGER, " +
                    COL_ACCOUNTS_IS_ACTIVE + " INTEGER, " +
                    COL_ACCOUNTS_IS_WRITER + " INTEGER)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

    public CentralDatabase(Context context) {
        this.context = context;
        helper = new CentralDBHelper(this.context);
    }

    public CentralDatabase open() {
        db = helper.getWritableDatabase();
        //Accounts.IsWriterの初期化を行う
        beginTransaction();
        try {
            db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + COL_ACCOUNTS_IS_WRITER + " = 0");
            db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + COL_ACCOUNTS_IS_WRITER + " = 1 WHERE " + COL_ACCOUNTS_IS_PRIMARY + " = 1");
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
        return this;
    }

    public void close() {
        db.close();
    }

    public void beginTransaction() {
        db.beginTransaction();
    }

    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    public void endTransaction() {
        db.endTransaction();
    }
}
