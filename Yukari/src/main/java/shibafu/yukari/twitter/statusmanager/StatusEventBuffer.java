package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Status;

/**
 * ツイートの一時的な保持とビューへの再配信機能を実装します。
 *
 * Created by shibafu on 2015/07/27.
 */
class StatusEventBuffer implements EventBuffer{
    private AuthUserRecord from;
    private Status status;
    private boolean muted;

    public StatusEventBuffer(AuthUserRecord from, PreformedStatus status, boolean muted) {
        this.from = from;
        this.status = status;
        this.muted = muted;
    }

    @Override
    public void sendBufferedEvent(StatusListener listener) {
        listener.onStatus(from, (PreformedStatus) status, muted);
    }
}
