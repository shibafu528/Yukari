package shibafu.yukari.twitter.statusimpl;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by shibafu on 14/12/20.
 * @deprecated should use new Status interface and {@link shibafu.yukari.entity.StatusPreforms#setRepostRespondTo(shibafu.yukari.entity.Status)}
 */
public class RespondNotifyStatus extends PreformedStatus {
    private Status respondTo;

    public RespondNotifyStatus(Status reaction, Status respondTo, AuthUserRecord receivedUser) {
        super(reaction, receivedUser);
        this.respondTo = respondTo;
        getQuoteEntities().add(respondTo.getId());
    }

    public RespondNotifyStatus(PreformedStatus reaction, Status respondTo) {
        super(reaction);
        this.respondTo = respondTo;
        getQuoteEntities().add(respondTo.getId());
    }

    @Override
    public long getInReplyToStatusId() {
        return respondTo.getId();
    }

    @Override
    public long getInReplyToUserId() {
        return respondTo.getUser().getId();
    }

    @Override
    public String getInReplyToScreenName() {
        return respondTo.getUser().getScreenName();
    }
}
