package shibafu.yukari.util;

/**
 * リソースの解放が必要であることを明示し、またその手段を提供します。
 *
 * Created by shibafu on 2015/07/28.
 */
public interface Releasable {
    /**
     * リソースの解放を行います。
     */
    void release();
}
