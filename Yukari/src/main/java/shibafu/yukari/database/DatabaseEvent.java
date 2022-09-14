package shibafu.yukari.database;

import android.content.Intent;

public final class DatabaseEvent {
    /**
     * {@link CentralDatabase#updateRecord} によってデータベースが更新された時に配信されるブロードキャスト
     */
    public static final String ACTION_UPDATE = "shibafu.yukari.database.ACTION_UPDATE";

    /**
     * (String) 更新されたテーブルのクラス名
     */
    public static final String EXTRA_CLASS = "class";

    /*package*/ static <T extends DBRecord> Intent updateTable(Class<T> cls) {
        Intent intent = new Intent(ACTION_UPDATE);
        intent.putExtra(EXTRA_CLASS, cls.getName());
        return intent;
    }
}
