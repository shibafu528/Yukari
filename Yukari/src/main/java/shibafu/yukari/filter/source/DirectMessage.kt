package shibafu.yukari.filter.source

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
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterMessage
import twitter4j.Paging
import twitter4j.Twitter
import twitter4j.TwitterException
import java.util.*

/**
 * 指定されたアカウントのDirectMessageおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
data class DirectMessage(override val sourceAccount: AuthUserRecord) : FilterSource {

    override fun getRestQuery() = object : RestQuery {
        // TwitterRestQueryのresponseList生成周辺をDMに書き換えただけ
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): MutableList<Status> {
            api as Twitter
            val paging = Paging()
            paging.count = params.limitCount
            if (params.maxId > -1) {
                paging.maxId = params.maxId
            }
            try {
                val inboxResponses: MutableList<Status> = api.getDirectMessages(paging).map { TwitterMessage(it, userRecord) }.toMutableList()
                val outboxResponses: MutableList<Status> = api.getSentDirectMessages(paging).map { TwitterMessage(it, userRecord) }.toMutableList()
                val responseList: MutableList<Status> = mutableListOf()
                responseList += inboxResponses
                responseList += outboxResponses

                if (params.appendLoadMarker) {
                    if (responseList.isEmpty()) {
                        responseList += LoadMarker(params.maxId, Provider.API_TWITTER, params.maxId, userRecord, params.loadMarkerTag, params.loadMarkerDate)
                    } else if (inboxResponses.isNotEmpty() && outboxResponses.isNotEmpty()) {
                        val lastIn = inboxResponses.last()
                        val lastOut = outboxResponses.last()
                        if (lastIn.id < lastOut.id) {
                            responseList += LoadMarker(lastIn.id - 1, Provider.API_TWITTER, lastIn.id, userRecord, params.loadMarkerTag, Date(lastIn.createdAt.time - 1))
                        } else {
                            responseList += LoadMarker(lastOut.id - 1, Provider.API_TWITTER, lastOut.id, userRecord, params.loadMarkerTag, Date(lastOut.createdAt.time - 1))
                        }
                    } else if (inboxResponses.isNotEmpty()) {
                        val last = inboxResponses.last()
                        responseList += LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1))
                    } else if (outboxResponses.isNotEmpty()) {
                        val last = outboxResponses.last()
                        responseList += LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1))
                    }
                }

                return responseList
            } catch (e: TwitterException) {
                throw RestQueryException(e)
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