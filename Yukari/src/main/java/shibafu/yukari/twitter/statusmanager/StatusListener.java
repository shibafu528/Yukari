package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * ストリーミングイベントの受信機能を提供します。
 *
 * Created by shibafu on 2015/07/28.
 */
public interface StatusListener {
    String getStreamFilter();

    void onStatus(AuthUserRecord from, PreformedStatus status, boolean muted);

    void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);

    void onUpdatedStatus(AuthUserRecord from, int kind, Status status);

    String getRestTag();

    /**
     * {@link StatusManager} から登録解除する際、次に接続した際に同一のTLであることを認識できるようにする識別子を返します。
     * ここで返した値が同一であれば、次に登録した際、登録解除中の受信データを受け取ることが出来ます。
     * @return TL識別子
     */
    String getSubscribeIdentifier();
}
