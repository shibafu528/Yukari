package shibafu.yukari.twitter.statusimpl;

/**
 * Created by shibafu on 2015/07/30.
 */
public class RestCompletedStatus extends FakeStatus {
    private String tag;
    private long taskKey;

    public RestCompletedStatus(String tag, long taskKey) {
        super(0);
        this.tag = tag;
        this.taskKey = taskKey;
    }

    public String getTag() {
        return tag;
    }

    public long getTaskKey() {
        return taskKey;
    }
}
