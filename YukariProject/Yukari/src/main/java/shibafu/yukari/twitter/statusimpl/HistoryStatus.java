package shibafu.yukari.twitter.statusimpl;

import java.util.Date;

import twitter4j.Status;
import twitter4j.User;

/**
 * Created by shibafu on 15/02/07.
 */
public class HistoryStatus extends FakeStatus{
    public static final int KIND_FAVED = 0;
    public static final int KIND_RETWEETED = 1;

    private final int kind;
    private final User eventBy;
    private final Status status;

    public HistoryStatus(long timeAtMillis, int kind, User eventBy, Status status) {
        super(timeAtMillis);
        this.kind = kind;
        this.eventBy = eventBy;
        this.status = status;
    }

    public int getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public User getUser() {
        return eventBy;
    }

    @Override
    public String getText() {
        return "";
    }

    @Override
    public Date getCreatedAt() {
        return new Date(getId());
    }
}
