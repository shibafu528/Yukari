package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.twitter.AuthUserRecord

/**
 * フィルタシステムにおける抽出ソースを表す抽象クラスです。
 *
 * データソースから情報を取得する手段や、必要な手続きがあるかの情報を持ちます。
 *
 * Created by shibafu on 15/06/07.
 */
interface FilterSource{
    /**
     * 抽出ソースへのアクセスを行うための認証情報を持つユーザアカウントです。
     * 一切の権限を要求しないソースの場合、nullになります。
     */
    val sourceAccount: AuthUserRecord?

    /**
     * この抽出ソースが要求するデータを取得するための、REST通信の実装を返します。
     * @return 対象データソースとの通信を行う [RestQuery]
     */
    fun getRestQuery() : RestQuery?

    /**
     * この抽出ソースによる、Stream受信に対して干渉するためのフィルタを返します。
     *
     * これは利用側では、各抽出ソースから取得したフィルタクエリの論理積をとって使用するものと想定します。
     * @return Stream受信に対するフィルタクエリ。
     */
    fun getStreamFilter(): SNode = ValueNode(true)

    /**
     * この抽出ソースが動的にStreamへの接続を要求する場合、接続先 [StreamChannel] のインスタンスを返します。
     * @return [StreamChannel] のインスタンス。動的な接続先を持たない場合、nullを返します。
     */
    fun getDynamicChannel(context: Context): StreamChannel? = null
}

//    何を実装すべきかの参考でしか無い
//    public enum class Resource {
//        ALL,
//        HOME,
//        MENTION,
//        LIST,
//        SEARCH,
//        TRACK,
//        TRACE,
//        USER,
//        FAVORITE,
//        BOOKMARK
//    }
