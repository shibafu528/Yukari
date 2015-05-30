package shibafu.dissonance.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.Collection;

import shibafu.dissonance.twitter.AuthUserRecord;

/**
 * Created by shibafu on 14/10/04.
 */
@DBTable(CentralDatabase.TABLE_USER_EXTRAS)
public class UserExtras implements DBRecord {
    private long id;
    private int color;
    private long priorityAccountId;
    private AuthUserRecord priorityAccount;

    public UserExtras(long id) {
        this.id = id;
    }

    public UserExtras(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_ID));
        this.color = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_COLOR));
        this.priorityAccountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_PRIORITY_ID));
    }

    public UserExtras(Cursor cursor, Collection<AuthUserRecord> userRecords) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_ID));
        this.color = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_COLOR));
        this.priorityAccountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_PRIORITY_ID));
        for (AuthUserRecord userRecord : userRecords) {
            if (this.priorityAccountId == userRecord.NumericId) {
                this.priorityAccount = userRecord;
                break;
            }
        }
    }

    public long getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public long getPriorityAccountId() {
        return priorityAccount == null ? priorityAccountId : getPriorityAccount().NumericId;
    }

    public AuthUserRecord getPriorityAccount() {
        return priorityAccount;
    }

    public void setPriorityAccount(AuthUserRecord priorityAccount) {
        this.priorityAccount = priorityAccount;
        this.priorityAccountId = priorityAccount != null ? priorityAccount.NumericId : 0;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(CentralDatabase.COL_UEXTRAS_ID, id);
        values.put(CentralDatabase.COL_UEXTRAS_COLOR, color);
        values.put(CentralDatabase.COL_UEXTRAS_PRIORITY_ID, getPriorityAccountId());
        return values;
    }
}
