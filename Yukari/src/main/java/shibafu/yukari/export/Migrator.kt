package shibafu.yukari.export

import shibafu.yukari.common.TabInfo
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.Provider
import shibafu.yukari.database.SearchHistory
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.database.UserExtras
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.twitter.AuthUserRecord

class AccountMigrator : ConfigFileMigrator<AuthUserRecord> {
    override val latestVersion = 2

    constructor() : super(AuthUserRecord::class.java, {
        // Version 2
        migrateTo(2) { config, _ ->
            config["UserId"] = config["_id"]
            config["ScreenName"] = ""
            config["DisplayName"] = ""
            config["ProfileImageUrl"] = ""

            config.remove("_id")
            config.remove("ConsumerKey")
            config.remove("ConsumerSecret")
        }
    })
}

class ProviderMigrator : ConfigFileMigrator<Provider> {
    override val latestVersion = 1

    constructor() : super(Provider::class.java, {})
}

class TabInfoMigrator : ConfigFileMigrator<TabInfo> {
    override val latestVersion = 2

    constructor() : super(TabInfo::class.java, {
        // Version 2
        migrateTo(2) { config, db ->
            // BindAccountIdをTwitter IDからInternal Account IDに変換
            findInternalAccountId(db, (config["BindAccountId"] as Double).toLong())?.let {
                config["BindAccountId"] = it
            }
        }
    })
}

class MuteConfigMigrator : ConfigFileMigrator<MuteConfig> {
    override val latestVersion = 1

    constructor() : super(MuteConfig::class.java, {})
}

class AutoMuteConfigMigrator : ConfigFileMigrator<AutoMuteConfig> {
    override val latestVersion = 1

    constructor() : super(AutoMuteConfig::class.java, {})
}

class UserExtrasMigrator : ConfigFileMigrator<UserExtras> {
    override val latestVersion = 2

    constructor() : super(UserExtras::class.java, {
        // Version 2
        migrateTo(2) { config, db ->
            // _idをURLに変換
            config["_id"] = "https://twitter.com/intent/user?user_id=${config["_id"]}"

            // PriorityAccountIdをTwitter IDからInternal Account IDに変換
            findInternalAccountId(db, (config["PriorityAccountId"] as Double).toLong())?.let {
                config["PriorityAccountId"] = it
            }
        }
    })
}

class BookmarkMigrator : ConfigFileMigrator<Bookmark.SerializeEntity> {
    override val latestVersion = 2

    constructor() : super(Bookmark.SerializeEntity::class.java, {
        // Version 2
        migrateTo(2) { config, db ->
            // ReceiverIdをTwitter IDからInternal Account IDに変換
            findInternalAccountId(db, (config["ReceiverId"] as Double).toLong())?.let {
                config["ReceiverId"] = it
            }
        }
    })
}

class StatusDraftMigrator : ConfigFileMigrator<StatusDraft> {
    override val latestVersion = 2

    constructor() : super(StatusDraft::class.java, {
        oldName(1..1, "TweetDraft")

        // Version 2
        migrateTo(2) { config, db ->
            // WriterIdをTwitter IDからInternal Account IDに変換
            findInternalAccountId(db, (config["WriterId"] as Double).toLong())?.let {
                config["WriterId"] = it
            }

            val isDirectMessage = (config["IsDirectMessage"] ?: "0").toString()
            val inReplyToId = (config["InReplyTo"] as Double).toLong()

            // InReplyToをURLに変換
            if (inReplyToId <= 0) {
                config["InReplyTo"] = null
            } else if (isDirectMessage == "1") {
                config["InReplyTo"] = "https://twitter.com/intent/user?user_id=$inReplyToId"
            } else {
                config["InReplyTo"] = "https://twitter.com/null/status/$inReplyToId"
            }

            // VisibilityはPublic固定
            config["Visibility"] = 0L
        }
    })
}

class SearchHistoryMigrator : ConfigFileMigrator<SearchHistory> {
    override val latestVersion = 1

    constructor() : super(SearchHistory::class.java, {})
}

class StreamChannelStateMigrator : ConfigFileMigrator<StreamChannelState> {
    override val latestVersion = 1

    constructor() : super(StreamChannelState::class.java, {})
}

/**
 * DB内のアカウント情報から、指定したTwitter IDに対応するレコードを検索してキーを返却する。
 * 見つからなかった場合は null を返す。
 * @param database データベース
 * @param userId 検索対象のTwitter ID
 * @return 内部アカウントID、ヒットしなかった場合は null
 */
private fun findInternalAccountId(database: CentralDatabase, userId: Long): Long? {
    database.accounts.use {
        if (!it.moveToFirst()) return null

        do {
            val id = it.getLong(it.getColumnIndex(CentralDatabase.COL_ACCOUNTS_ID))
            val uid = it.getLong(it.getColumnIndex(CentralDatabase.COL_ACCOUNTS_USER_ID))
            val isTwitterAccount = it.isNull(it.getColumnIndex(CentralDatabase.COL_ACCOUNTS_PROVIDER_ID))

            if (isTwitterAccount && uid == userId) {
                return id
            }
        } while (it.moveToNext())
    }

    return null
}