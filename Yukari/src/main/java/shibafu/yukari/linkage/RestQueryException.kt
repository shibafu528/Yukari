package shibafu.yukari.linkage

/**
 * [RestQuery] 処理中に発生した例外のラッパー
 */
class RestQueryException(cause: Throwable) : Exception(cause)