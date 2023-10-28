package shibafu.yukari.mastodon

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import java.util.Date

/**
 * RestQueryのMastodon用テンプレート
 */
abstract class AbstractMastodonRestQuery<ApiEntity, StatusEntity : Status> : RestQuery {
    override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): MutableList<Status> {
        api as MastodonClient
        val range = Range(maxId = if (params.maxId > -1) params.maxId else null, limit = params.limitCount)

        try {
            val response = resolve(api, range)
            val list: MutableList<Status> = response.part.map { mapToEntity(it, userRecord) }.toMutableList()

            if (params.appendLoadMarker) {
                list += if (list.isEmpty()) {
                    LoadMarker(params.maxId, Provider.API_MASTODON, params.maxId, userRecord, params.loadMarkerTag, params.loadMarkerDate)
                } else {
                    val last = list.last()
                    LoadMarker(last.id - 1, Provider.API_MASTODON, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1000))
                }
            }

            return list
        } catch (e: Mastodon4jRequestException) {
            throw RestQueryException(userRecord, e)
        }
    }

    abstract fun resolve(client: MastodonClient, range: Range) : Pageable<ApiEntity>

    abstract fun mapToEntity(record: ApiEntity, userRecord: AuthUserRecord) : StatusEntity
}