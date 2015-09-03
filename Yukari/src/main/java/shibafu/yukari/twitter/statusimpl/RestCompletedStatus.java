package shibafu.yukari.twitter.statusimpl;

/**
 * Created by shibafu on 2015/07/30.
 */
public class RestCompletedStatus extends FakeStatus {
    private String tag;

    public RestCompletedStatus(String tag) {
        super(0);
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
