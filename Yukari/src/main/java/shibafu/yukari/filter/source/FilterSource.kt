package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.PreformedResponseList
import shibafu.yukari.twitter.RESTLoader
import shibafu.yukari.twitter.rest.RESTParams
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusmanager.RestQuery
import shibafu.yukari.twitter.streaming.FilterStream
import twitter4j.Paging
import twitter4j.Twitter

/**
 * フィルタシステムにおける抽出ソースを表す抽象クラスです。
 *
 * データソースから情報を取得する手段や、必要な手続きがあるかの情報を持ちます。
 *
 * Created by shibafu on 15/06/07.
 */
public interface FilterSource{
    /**
     * 抽出ソースへのアクセスを行うための認証情報を持つユーザアカウントです。
     * 一切の権限を要求しないソースの場合、nullになります。
     */
    val sourceAccount: AuthUserRecord?

    /**
     * この抽出ソースが要求するデータを取得するための、REST通信の実装を返します。
     * @return 対象データソースとの通信を行う[RestQuery]
     */
    fun getRestQuery() : RestQuery?

    /**
     * この抽出ソースが、割り当てられているアカウントでのUserStreamの接続を要求するかを返します。
     * @return UserStreamを必要とする場合はtrue。その場合、利用側ではエンドユーザによって拒否されていない限りUserStreamへの接続を行う必要があります。
     */
    fun requireUserStream(): Boolean

    /**
     * この抽出ソースがFilterStreamへの接続を要求する場合、必要なパラメータを設定したインスタンスを返します。
     * @return 検索条件の設定を行ったFilterStreamインスタンス。FilterStreamへの接続を必要としない場合、nullを返します。
     */
    fun getFilterStream(context: Context): FilterStream?
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
