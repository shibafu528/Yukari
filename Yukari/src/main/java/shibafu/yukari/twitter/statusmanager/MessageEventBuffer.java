package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.DirectMessage;

/**
 * ダイレクトメッセージの一時的な保持とビューへの再配信機能を実装します。
 *
 * Created by shibafu on 2015/07/27.
 */
class MessageEventBuffer implements EventBuffer{
    private AuthUserRecord from;
    private DirectMessage directMessage;

    public MessageEventBuffer(AuthUserRecord from, DirectMessage directMessage) {
        this.from = from;
        this.directMessage = directMessage;
    }

    @Override
    public void sendBufferedEvent(StatusManager.StatusListener listener) {
        listener.onDirectMessage(from, directMessage);
    }
}
