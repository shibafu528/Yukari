package shibafu.yukari.twitter.statusmanager;

/**
 * 配信先を一時的に失った受信ステータスの、データの保持と再配信機能を提供します。
 *
 * Created by shibafu on 2015/07/27.
 */
interface EventBuffer {
    /**
     * 指定のリスナに対してステータスの再配信を実行します。
     * @param listener 配信先リスナ
     */
    void sendBufferedEvent(StatusListener listener);
}
