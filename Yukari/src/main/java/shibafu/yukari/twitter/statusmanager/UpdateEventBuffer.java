package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * データ更新イベントの一時的な保持とビューへの再配信機能を実装します。
 *
 * Created by shibafu on 2015/07/27.
 */
class UpdateEventBuffer implements EventBuffer{
    public static final int UPDATE_FAVED = 1;
    public static final int UPDATE_UNFAVED = 2;
    public static final int UPDATE_DELETED = 3;
    public static final int UPDATE_DELETED_DM = 4;
    public static final int UPDATE_NOTIFY = 5;
    public static final int UPDATE_FORCE_UPDATE_UI = 6;
    public static final int UPDATE_WIPE_TWEETS = 0xff;

    private AuthUserRecord from;
    private Status status;
    private int kind;

    public UpdateEventBuffer(AuthUserRecord from, int kind, Status status) {
        this.from = from;
        this.kind = kind;
        this.status = status;
    }

    @Override
    public void sendBufferedEvent(StatusManager.StatusListener listener) {
        listener.onUpdatedStatus(from, kind, status);
    }
}
