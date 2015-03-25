package shibafu.yukari.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by Shibafu on 13/08/12.
 */
public class LinkProvider extends ContentProvider{

    private final static UriMatcher URI_MATCHER;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI("shibafu.yukari.af2015.link", "user/*", 1);
        URI_MATCHER.addURI("shibafu.yukari.af2015.link", "hash/*", 2);
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case 1:
                return "vnd.shibafu.yukari.af2015/user";
            case 2:
                return "vnd.shibafu.yukari.af2015/hash";
            default:
                throw new IllegalArgumentException("Unknown URL:" + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
