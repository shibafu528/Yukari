package shibafu.yukari.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/18.
 */
public class CentralDatabase {

    //DB基本情報
    public static final String DB_FILENAME = "yukari.db";
    public static final int DB_VER = 7;

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
    public static final String COL_DRAFTS_MESSAGE_TARGET = "MessageTarget";

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

    //SearchHistoryテーブル
    public static final String TABLE_SEARCH_HISTORY = "SearchHistory";
    public static final String COL_SHISTORY_ID = "_id";
    public static final String COL_SHISTORY_QUERY = "query";
    public static final String COL_SHISTORY_DATE = "date";

    //Muteテーブル
    public static final String TABLE_MUTE = "Mute";
    public static final String COL_MUTE_ID = "_id";
    public static final String COL_MUTE_SCOPE = "Scope";
    public static final String COL_MUTE_MATCH = "Match";
    public static final String COL_MUTE_MUTE = "Mute";
    public static final String COL_MUTE_QUERY = "Query";
    public static final String COL_MUTE_EXPIRATION_TIME_MILLIS = "ExpirationTimeMillis"; //"ExpirationDate";

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
                    COL_DRAFTS_IS_FAILED_DELIVERY + " INTEGER, " +
                    COL_DRAFTS_MESSAGE_TARGET + " TEXT DEFAULT '')"
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
            {
                TabInfo homeTab = new TabInfo(TabType.TABTYPE_HOME, 0, null);
                db.insert(TABLE_TABS, null, homeTab.getContentValues());
                TabInfo mentionTab = new TabInfo(TabType.TABTYPE_MENTION, 1, null);
                db.insert(TABLE_TABS, null, mentionTab.getContentValues());
                TabInfo dmTab = new TabInfo(TabType.TABTYPE_DM, 2, null);
                db.insert(TABLE_TABS, null, dmTab.getContentValues());
            }
            db.execSQL(
                    "CREATE TABLE " + TABLE_SEARCH_HISTORY + " (" +
                    COL_SHISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_SHISTORY_QUERY + " TEXT UNIQUE, " +
                    COL_SHISTORY_DATE + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_MUTE + " (" +
                            COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COL_MUTE_SCOPE + " INTEGER, " +
                            COL_MUTE_MATCH + " INTEGER, " +
                            COL_MUTE_MUTE + " INTEGER, " +
                            COL_MUTE_QUERY + " TEXT, " +
                            COL_MUTE_EXPIRATION_TIME_MILLIS + " INTEGER DEFAULT -1)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1) {
                TabInfo homeTab = new TabInfo(TabType.TABTYPE_HOME, 0, null);
                db.insert(TABLE_TABS, null, homeTab.getContentValues());
                TabInfo mentionTab = new TabInfo(TabType.TABTYPE_MENTION, 1, null);
                db.insert(TABLE_TABS, null, mentionTab.getContentValues());
                TabInfo dmTab = new TabInfo(TabType.TABTYPE_DM, 2, null);
                db.insert(TABLE_TABS, null, dmTab.getContentValues());
                ++oldVersion;
            }
            if (oldVersion == 2) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_SEARCH_HISTORY + " (" +
                        COL_SHISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_SHISTORY_QUERY + " TEXT UNIQUE, " +
                        COL_SHISTORY_DATE + " INTEGER)"
                );
                ++oldVersion;
            }
            if (oldVersion == 3) {
                db.execSQL(
                        "ALTER TABLE " + TABLE_DRAFTS + " ADD " + COL_DRAFTS_MESSAGE_TARGET +
                        " TEXT DEFAULT ''"
                );
                ++oldVersion;
            }
            if (oldVersion == 4) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_MUTE + " (" +
                                COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_MUTE_SCOPE + " INTEGER, " +
                                COL_MUTE_MATCH + " INTEGER, " +
                                COL_MUTE_MUTE + " INTEGER, " +
                                COL_MUTE_QUERY + " TEXT)"
                );
                ++oldVersion;
            }
            if (oldVersion == 5) {
                db.execSQL("ALTER TABLE " + TABLE_MUTE + " ADD ExpirationDate INTEGER DEFAULT -1");
                ++oldVersion;
            }
            if (oldVersion == 6) {
                db.execSQL("ALTER TABLE " + TABLE_MUTE + " RENAME TO tmp_Mute");
                db.execSQL(
                        "CREATE TABLE " + TABLE_MUTE + " (" +
                                COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_MUTE_SCOPE + " INTEGER, " +
                                COL_MUTE_MATCH + " INTEGER, " +
                                COL_MUTE_MUTE + " INTEGER, " +
                                COL_MUTE_QUERY + " TEXT, " +
                                COL_MUTE_EXPIRATION_TIME_MILLIS + " INTEGER DEFAULT -1)"
                );
                db.execSQL("INSERT INTO " + TABLE_MUTE + "(" +
                        COL_MUTE_ID + ", " +
                        COL_MUTE_SCOPE + ", " +
                        COL_MUTE_MATCH + ", " +
                        COL_MUTE_MUTE + ", " +
                        COL_MUTE_QUERY + ", " +
                        COL_MUTE_EXPIRATION_TIME_MILLIS + ") SELECT " +
                        COL_MUTE_ID + ", " +
                        COL_MUTE_SCOPE + ", " +
                        COL_MUTE_MATCH + ", " +
                        COL_MUTE_MUTE + ", " +
                        COL_MUTE_QUERY + ", " +
                        "ExpirationDate FROM tmp_Mute");
                db.execSQL("DROP TABLE tmp_Mute");
                ++oldVersion;
            }
        }
    }

    public CentralDatabase(Context context) {
        helper = new CentralDBHelper(context);
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

    public void vacuum() {
        db.execSQL("vacuum");
    }

    //<editor-fold desc="Users">
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

    public DBUser getUser(String screenName) {
        Cursor c = db.query(TABLE_USER, null, COL_USER_SCREEN_NAME + "=?", new String[]{screenName}, null, null, null);
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

    public Cursor getUsersCursor(String where, String[] whereClause) {
        return db.query(TABLE_USER, null, where, whereClause, null, null, COL_USER_SCREEN_NAME + " ASC");
    }

    public Cursor getUsersCursor() {
        return db.query(TABLE_USER, null, null, null, null, null, COL_USER_SCREEN_NAME + " ASC");
    }

    public void wipeUsers() {
        beginTransaction();
        try {
            db.delete(TABLE_USER, COL_USER_ID + " NOT IN (SELECT " + COL_ACCOUNTS_ID + " FROM " + TABLE_ACCOUNTS + ")", null);
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Accounts">
    public Cursor getAccounts() {
        return db.query(
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

    //<editor-fold desc="Drafts">
    public void updateDraft(TweetDraft draft) {
        beginTransaction();
        try {
            deleteDraft(draft);
            for (ContentValues values : draft.getContentValuesArray()) {
                db.insert(TABLE_DRAFTS, null, values);
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
        List<TweetDraft> draftList = new ArrayList<>();
        Cursor cursor = db.query(
                TABLE_DRAFTS + "," + TABLE_ACCOUNTS + "," + TABLE_USER,
                null,
                COL_DRAFTS_WRITER_ID + "=" + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + " AND " +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "=" + TABLE_USER + "." + COL_USER_ID,
                null, null, null, COL_DRAFTS_DATETIME);
        try {
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
    //</editor-fold>

    //<editor-fold desc="Tabs">
    public void updateTab(TabInfo tabInfo) {
        ContentValues values = tabInfo.getContentValues();
        if (values.containsKey(COL_TABS_ID)) {
            db.replace(TABLE_TABS, null, values);
        }
        else {
            db.insert(TABLE_TABS, null, values);
        }
    }

    public void deleteTab(int id) {
        db.delete(TABLE_TABS, COL_TABS_ID + "=" + id, null);
    }

    public ArrayList<TabInfo> getTabs() {
        Cursor cursor = db.rawQuery("SELECT " + joinColumnName(TABLE_TABS, COL_TABS_ID) + " AS _id_t, * FROM " +
                TABLE_TABS + " LEFT OUTER JOIN " + TABLE_ACCOUNTS + " ON " +
                TABLE_TABS + "." + COL_TABS_BIND_ACCOUNT_ID + " = " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID +
                " LEFT OUTER JOIN " + TABLE_USER + " ON " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + " = " + TABLE_USER + "." + COL_USER_ID +
                " ORDER BY " + COL_TABS_TAB_ORDER, null
        );
        ArrayList<TabInfo> tabs = new ArrayList<>();
        try {
            if (cursor.moveToFirst()) {
                do {
                    TabInfo info = new TabInfo(cursor);
                    tabs.add(info);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return tabs;
    }
    //</editor-fold>

    //<editor-fold desc="SearchHistory">
    public List<SearchHistory> getSearchHistories() {
        List<SearchHistory> searchHistories = new ArrayList<>();
        Cursor cursor = db.query(TABLE_SEARCH_HISTORY, null, null, null, null, null, COL_SHISTORY_DATE + " DESC");
        try {
            if (cursor.moveToFirst()) do {
                searchHistories.add(new SearchHistory(cursor));
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return searchHistories;
    }

    public void updateSearchHistory(String query) {
        db.replace(TABLE_SEARCH_HISTORY, null,
                new SearchHistory(query, new Date(System.currentTimeMillis())).getContentValues());
        db.execSQL("DELETE FROM " + TABLE_SEARCH_HISTORY + " WHERE " + COL_SHISTORY_ID + " IN (" +
                        "SELECT " + COL_SHISTORY_ID + " FROM " + TABLE_SEARCH_HISTORY +
                        " ORDER BY " + COL_SHISTORY_DATE + " DESC LIMIT -1 OFFSET 10)"
        );
    }

    public void deleteSearchHistory(long id) {
        db.delete(TABLE_SEARCH_HISTORY, COL_SHISTORY_ID + "=" + id, null);
    }
    //</editor-fold>

    //<editor-fold desc="Mute">
    public List<MuteConfig> getMuteConfig() {
        List<MuteConfig> muteConfigs = new ArrayList<>();
        Cursor cursor = db.query(TABLE_MUTE, null, null, null, null, null, null);
        try {
            if (cursor.moveToFirst()) do {
                muteConfigs.add(new MuteConfig(cursor));
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return muteConfigs;
    }

    public void updateMuteConfig(MuteConfig muteConfig) {
        db.replace(TABLE_MUTE, null, muteConfig.getContentValues());
    }

    public void deleteMuteConfig(long id) {
        db.delete(TABLE_MUTE, COL_MUTE_ID + "=" + id, null);
    }

    public void importMuteConfigs(List<MuteConfig> muteConfigs) {
        beginTransaction();
        try {
            db.delete(TABLE_MUTE, null, null);
            for (MuteConfig muteConfig : muteConfigs) {
                db.insert(TABLE_MUTE, null, muteConfig.getContentValues());
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }
    //</editor-fold>

    public static String joinColumnName(String table, String column) {
        return table + "." + column;
    }
}
