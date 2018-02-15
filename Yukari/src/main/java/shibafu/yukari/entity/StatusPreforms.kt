package shibafu.yukari.entity

import org.eclipse.collections.api.map.primitive.MutableLongBooleanMap
import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap
import java.io.Serializable

/**
 * 主に前処理の段階で決定しておく、ステータスのメタ情報など
 */
class StatusPreforms : Serializable {
    /**
     * 表示すべきでないメディアを含んでいるかどうか
     */
    var isCensoredThumbs: Boolean = false

    /**
     * RTレスポンスの対象ステータス (このステータスが、どのステータスに関連したと思われるものなのか)
     */
    var repostRespondTo: Status? = null

    /**
     * お気に入り登録
     */
    var favoritedUsers: MutableLongBooleanMap = LongBooleanHashMap() // TODO: 表示側は？
}