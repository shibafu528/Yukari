package shibafu.yukari.filter.source

import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterMessage
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.User
import java.util.*

/**
 * 指定されたアカウントのDirectMessageおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
@Source(apiType = Provider.API_TWITTER, slug = "message")
data class DirectMessage(override val sourceAccount: AuthUserRecord) : FilterSource {

    override fun getRestQuery() = object : RestQuery {
        // TwitterRestQueryのresponseList生成周辺をDMに書き換えただけ
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): MutableList<Status> {
            api as Twitter
            val cursor = params.stringCursor
            val count = params.limitCount.coerceIn(20..50)

            try {
                val messages = if (cursor.isEmpty()) {
                    api.getDirectMessages(count)
                } else {
                    api.getDirectMessages(count, cursor)
                }

                // レスポンスに含まれるユーザの情報を一括取得
                val userIds = messages.flatMap { listOf(it.senderId, it.recipientId) }.distinct().toLongArray()
                val users: Set<User> = if (userIds.isNotEmpty()) {
                    api.lookupUsers(*userIds).toSortedSet()
                } else {
                    emptySet()
                }

                val responseList: MutableList<Status> = messages.mapNotNull { dm ->
                    val sender = users.firstOrNull { u -> u.id == dm.senderId } ?: return@mapNotNull null
                    val recipient = users.firstOrNull { u -> u.id == dm.recipientId } ?: return@mapNotNull null
                    TwitterMessage(dm, sender, recipient, userRecord)
                }.toMutableList()

                if (params.appendLoadMarker && !messages.nextCursor.isNullOrEmpty()) {
                    if (responseList.isEmpty()) {
                        responseList += LoadMarker(params.maxId, Provider.API_TWITTER, params.maxId, userRecord, params.loadMarkerTag, params.loadMarkerDate, messages.nextCursor)
                    } else {
                        val last = responseList.last()
                        responseList += LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1000), messages.nextCursor)
                    }
                }

                return responseList
            } catch (e: TwitterException) {
                throw RestQueryException(userRecord, e)
            }
        }
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                VariableNode("receivedUsers"),
                ValueNode(sourceAccount)
            ),
            EqualsNode(
                VariableNode("class.simpleName"),
                ValueNode("TwitterMessage")
            )
    )
}