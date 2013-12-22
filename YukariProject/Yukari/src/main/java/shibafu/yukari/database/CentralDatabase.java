package shibafu.yukari.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.twitter.AuthUserRecord;

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
    public static final String COL_ACCOUNTS_FALLBACK_TO= "FallbackTo"; //投稿規制フォールバック先ID、使わない場合は0

    //Userテーブル
    public static final String TABLE_USER = "User";
    public static final String COL_USER_ID = "_id";
    public static final String COL_USER_SCREEN_NAME = "ScreenName";
    public static final String COL_USER_NAME = "Name";
    public static final String COL_USER_DESCRIPTION = "Description";
    public static final String COL_USER_DESCRIPTION_URLENTITIES = "DescriptionURLEntities";
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
    public static final String COL_DRAFTS_ID = "_id";
    public static final String COL_DRAFTS_WRITER_ID = "WriterId";
    public static final String COL_DRAFTS_TEXT = "Text";
    public static final String COL_DRAFTS_DATETIME = "DateTime";
    public static final String COL_DRAFTS_IN_REPLY_TO = "InReplyTo";//IsDirectMessage時は送信先ユーザIDを格納するカラムとする
    public static final String COL_DRAFTS_IS_QUOTED = "IsQuoted";
    public static final String COL_DRAFTS_ATTACHED_PICTURE = "AttachedPicture";
    public static final String COL_DRAFTS_USE_GEO_LOCATION = "UseGeoLocation";
    public static final String COL_DRAFTS_GEO_LATITUDE = "GeoLatitude";
    public static final String COL_DRAFTS_GEO_LONGITUDE = "GeoLongitude";
    public static final String COL_DRAFTS_IS_POSSIBLY_SENSITIVE = "IsPossiblySensitive";
    public static final String COL_DRAFTS_IS_DIRECT_MESSAGE = "IsDirectMessage";
    public static final String COL_DRAFTS_IS_FAILED_DELIVERY= "IsFailedDelivery";//送信失敗が原因で保存されたツイート

    //Bookmarksテーブル
    //一旦あきらめた!! ✌(('ω'✌ ))三✌(('ω'))✌三(( ✌'ω'))✌

    //Tabsテーブル
    public static final String TABLE_TABS = "Tabs";
    public static final String COL_TABS_ID = "_id";
    public static final String COL_TABS_TYPE = "Type";
    public static final String COL_TABS_TAB_ORDER = "TabOrder";
    public static final String COL_TABS_BIND_ACCOUNT_ID = "BindAccountId";
    public static final String COL_TABS_BIND_LIST_ID = "BindListId";
    public static final String COL_TABS_SEARCH_KEYWORD = "SearchKeyword";
    public static final String COL_TABS_FILTER_QUERY = "FilterQuery";

    //Tabs.Type
    public static final int TABTYPE_HOME = 0;
    public static final int TABTYPE_MENTION = 1;
    public static final int TABTYPE_DM = 2;
    public static final int TABTYPE_HISTORY = 3;
    public static final int TABTYPE_LIST = 4;
    public static final int TABTYPE_SEARCH = 5;
    public static final int TABTYPE_SEARCH_STREAM = 6;
    public static final int TABTYPE_FILTER = 7;

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
                    COL_ACCOUNTS_IS_WRITER + " INTEGER, " +
                    COL_ACCOUNTS_FALLBACK_TO + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_USER + " (" +
                    COL_USER_ID + " INTEGER PRIMARY KEY, " +
                    COL_USER_SCREEN_NAME + " TEXT, " +
                    COL_USER_NAME + " TEXT, " +
                    COL_USER_DESCRIPTION + " TEXT, " +
                    COL_USER_DESCRIPTION_URLENTITIES + " BLOB," +
                    COL_USER_LOCATION + " TEXT, " +
                    COL_USER_URL + " TEXT, " +
                    COL_USER_PROFILE_IMAGE_URL + " TEXT, " +
                    COL_USER_PROFILE_BANNER_URL + " TEXT, " +
                    COL_USER_IS_PROTECTED + " INTEGER, " +
                    COL_USER_IS_VERIFIED + " INTEGER, " +
                    COL_USER_IS_TRANSLATOR + " INTEGER, " +
                    COL_USER_IS_CONTRIBUTORS_ENABLED + " INTEGER, " +
                    COL_USER_IS_GEO_ENABLED + " INTEGER, " +
                    COL_USER_STATUSES_COUNT + " INTEGER, " +
                    COL_USER_FOLLOWINGS_COUNT + " INTEGER, " +
                    COL_USER_FOLLOWERS_COUNT + " INTEGER, " +
                    COL_USER_FAVORITES_COUNT + " INTEGER, " +
                    COL_USER_LISTED_COUNT + " INTEGER, " +
                    COL_USER_LANGUAGE + " TEXT, " +
                    COL_USER_CREATED_AT + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_DRAFTS + " (" +
                    COL_DRAFTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_DRAFTS_WRITER_ID + " INTEGER, " +
                    COL_DRAFTS_TEXT + " TEXT, " +
                    COL_DRAFTS_DATETIME + " TEXT, " +
                    COL_DRAFTS_IN_REPLY_TO + " INTEGER, " +
                    COL_DRAFTS_IS_QUOTED + " INTEGER, " +
                    COL_DRAFTS_ATTACHED_PICTURE + " TEXT, " +
                    COL_DRAFTS_USE_GEO_LOCATION + " INTEGER, " +
                    COL_DRAFTS_GEO_LATITUDE + " REAL, " +
                    COL_DRAFTS_GEO_LONGITUDE + " REAL, " +
                    COL_DRAFTS_IS_POSSIBLY_SENSITIVE + " INTEGER, " +
                    COL_DRAFTS_IS_DIRECT_MESSAGE + " INTEGER, " +
                    COL_DRAFTS_IS_FAILED_DELIVERY + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_TABS + " (" +
                    COL_TABS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TABS_TYPE + " INTEGER, " +
                    COL_TABS_TAB_ORDER + " INTEGER, " +
                    COL_TABS_BIND_ACCOUNT_ID + " INTEGER, " +
                    COL_TABS_BIND_LIST_ID + " INTEGER, " +
                    COL_TABS_SEARCH_KEYWORD + " TEXT, " +
                    COL_TABS_FILTER_QUERY + " TEXT)"
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

    public void updateUser(DBUser user) {
        db.replace(TABLE_USER, null, user.getContentValues());
    }

    public DBUser getUser(long id) {
        Cursor c = db.query(TABLE_USER, null, COL_USER_ID + "=" + id, null, null, null, null);
        DBUser user = null;
        try {
            if (c.moveToFirst()) {
                user = new DBUser(c);
            }
        } finally {
            c.close();
        }
        return user;
    }

    //<editor-fold desc="Accounts">
    public Cursor getAccounts() {
        Cursor cursor = db.query(
                TABLE_ACCOUNTS + "," + TABLE_USER,
                new String[]{
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID,
                        COL_ACCOUNTS_ACCESS_TOKEN,
                        COL_ACCOUNTS_ACCESS_TOKEN_SECRET,
                        COL_ACCOUNTS_IS_PRIMARY,
                        COL_ACCOUNTS_IS_ACTIVE,
                        COL_ACCOUNTS_IS_WRITER,
                        COL_USER_SCREEN_NAME,
                        COL_USER_NAME,
                        COL_USER_PROFILE_IMAGE_URL},
                TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "=" + TABLE_USER + "." + COL_USER_ID,
                null, null, null, null);
        return cursor;
    }

    public void addAccount(AuthUserRecord aur) {
        ContentValues contentValues = aur.getContentValues();
        Cursor c = db.query(TABLE_ACCOUNTS, null, null, null, null, null, null);
        int count = c.getCount();
        c.close();
        if (count == 0) {
            contentValues.put(COL_ACCOUNTS_IS_PRIMARY, 1);
        }
        db.replaceOrThrow(TABLE_ACCOUNTS, null, contentValues);
    }

    public AuthUserRecord getPrimaryAccount() {
        Cursor cursor = db.query(
                TABLE_ACCOUNTS + "," + TABLE_USER,
                new String[]{
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID,
                        COL_ACCOUNTS_ACCESS_TOKEN,
                        COL_ACCOUNTS_ACCESS_TOKEN_SECRET,
                        COL_ACCOUNTS_IS_PRIMARY,
                        COL_ACCOUNTS_IS_ACTIVE,
                        COL_ACCOUNTS_IS_WRITER,
                        COL_USER_SCREEN_NAME,
                        COL_USER_NAME,
                        COL_USER_PROFILE_IMAGE_URL},
                TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "=" + TABLE_USER + "." + COL_USER_ID +
                        " AND " + COL_ACCOUNTS_IS_PRIMARY + " = 1",
                null, null, null, null);
        AuthUserRecord primaryUser = null;
        if (cursor.getCount() > 0) {
            primaryUser = AuthUserRecord.getAccountsList(cursor).get(0);
        }
        return primaryUser;
    }

    public void updateAccount(AuthUserRecord aur) {
        db.replace(TABLE_ACCOUNTS, null, aur.getContentValues());
    }

    public void deleteAccount(long id) {
        db.delete(TABLE_ACCOUNTS, COL_ACCOUNTS_ID + "=" + id, null);
    }
    //</editor-fold>

    public void updateDraft(TweetDraft draft) {
        beginTransaction();
        try {
            for (ContentValues values : draft.getContentValuesArray()) {
                if (values.containsKey(COL_DRAFTS_ID)) {
                    db.replace(TABLE_DRAFTS, null, values);
                }
                else {
                    db.insert(TABLE_DRAFTS, null, values);
                }
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public void deleteDraft(TweetDraft draft) {
        deleteDraft(draft.getDateTime());
    }

    public void deleteDraft(long savedTime) {
        db.delete(TABLE_DRAFTS, COL_DRAFTS_DATETIME + "=" + savedTime, null);
    }

    public List<TweetDraft> getDrafts() {
        List<TweetDraft> draftList = new ArrayList<TweetDraft>();
        Cursor cursor = db.query(
                TABLE_DRAFTS + "," + TABLE_ACCOUNTS + "," + TABLE_USER,
                null,
                COL_DRAFTS_WRITER_ID + "=" + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + " AND " +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "=" + TABLE_USER + "." + COL_USER_ID,
                null, null, null, COL_DRAFTS_DATETIME);
        try {
            long lastDateTime = -1;
            TweetDraft last = null;
            TweetDraft draft;
            if (cursor.moveToFirst()) do {
                draft = new TweetDraft(cursor);
                if (last != null && last.getDateTime() == draft.getDateTime()) {
                    last.addWriter(new AuthUserRecord(cursor));
                }
                else {
                    draft.addWriter(new AuthUserRecord(cursor));
                    draftList.add(draft);
                    last = draft;
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return draftList;
    }
}
