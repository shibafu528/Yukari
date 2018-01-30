package shibafu.yukari.entity

import java.io.Serializable

/**
 * 主に前処理の段階で決定しておく、ステータスのメタ情報など
 */
class StatusPreforms : Serializable {
    /**
     * 表示すべきでないメディアを含んでいるかどうか
     */
    var isCensoredThumbs: Boolean = false
}