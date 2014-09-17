package shibafu.yukari.twitter.statusimpl;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by shibafu on 14/09/06.
 */
public class MetaStatus extends PreformedStatus {
    private Object metadata;

    public MetaStatus(Status status, AuthUserRecord receivedUser, Object metadata) {
        super(status, receivedUser);
        this.metadata = metadata;
    }

    public MetaStatus(PreformedStatus status, Object metadata) {
        super(status);
        this.metadata = metadata;
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return String.format("[%s]@%s: %s", metadata.toString(), getUser().getScreenName(), getText());
    }
}
