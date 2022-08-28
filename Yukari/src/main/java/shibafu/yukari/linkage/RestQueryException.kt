package shibafu.yukari.linkage

import shibafu.yukari.database.AuthUserRecord

/**
 * [RestQuery] 処理中に発生した例外のラッパー
 */
class RestQueryException(val userRecord: AuthUserRecord, cause: Throwable) : Exception(cause)