package shibafu.yukari.database;

import android.content.ContentValues;
import android.database.Cursor;
import shibafu.yukari.twitter.AuthUserRecord;

import java.util.Collection;

/**
 * Created by shibafu on 14/10/04.
 */
@DBTable(CentralDatabase.TABLE_USER_EXTRAS)
public class UserExtras implements DBRecord {
    private String id;
    private int color;
    private long priorityAccountId;
    private AuthUserRecord priorityAccount;

    public UserExtras(String id) {
        this.id = id;
    }

    public UserExtras(Cursor cursor) {
        this.id = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_ID));
        this.color = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_COLOR));
        this.priorityAccountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_PRIORITY_ID));
    }

    public UserExtras(Cursor cursor, Collection<AuthUserRecord> userRecords) {
        this.id = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_ID));
        this.color = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_COLOR));
        this.priorityAccountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_UEXTRAS_PRIORITY_ID));
        for (AuthUserRecord userRecord : userRecords) {
            if (this.priorityAccountId == userRecord.InternalId) {
                this.priorityAccount = userRecord;
                break;
            }
        }
    }

    /**
     * 対象ユーザのURL (IDという呼称は過去のものを引きずっているだけ)
     * @return
     */
    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public long getPriorityAccountId() {
        return priorityAccount == null ? priorityAccountId : getPriorityAccount().InternalId;
    }

    public void setPriorityAccountId(long priorityAccountId) {
        this.priorityAccountId = priorityAccountId;
    }

    public AuthUserRecord getPriorityAccount() {
        return priorityAccount;
    }

    public void setPriorityAccount(AuthUserRecord priorityAccount) {
        this.priorityAccount = priorityAccount;
        this.priorityAccountId = priorityAccount != null ? priorityAccount.InternalId : 0;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(CentralDatabase.COL_UEXTRAS_ID, id);
        values.put(CentralDatabase.COL_UEXTRAS_COLOR, color);
        values.put(CentralDatabase.COL_UEXTRAS_PRIORITY_ID, getPriorityAccountId());
        return values;
    }

    @Override
    public String toString() {
        return "UserExtras{" +
                "id=" + id +
                ", color=" + color +
                ", priorityAccountId=" + priorityAccountId +
                ", priorityAccount=" + priorityAccount +
                '}';
    }
}
