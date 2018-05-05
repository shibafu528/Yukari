package shibafu.yukari.export

import shibafu.yukari.common.TabInfo
import shibafu.yukari.common.TweetDraft
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.SearchHistory
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.database.UserExtras
import shibafu.yukari.twitter.AuthUserRecord

class AccountMigrator : ConfigFileMigrator<AuthUserRecord> {
    override val latestVersion = 1

    constructor() : super(AuthUserRecord::class.java, {})
}

class TabInfoMigrator : ConfigFileMigrator<TabInfo> {
    override val latestVersion = 1

    constructor() : super(TabInfo::class.java, {})
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
    override val latestVersion = 1

    constructor() : super(UserExtras::class.java, {})
}

class BookmarkMigrator : ConfigFileMigrator<Bookmark.SerializeEntity> {
    override val latestVersion = 1

    constructor() : super(Bookmark.SerializeEntity::class.java, {})
}

class TweetDraftMigrator : ConfigFileMigrator<TweetDraft> {
    override val latestVersion = 1

    constructor() : super(TweetDraft::class.java, {})
}

class SearchHistoryMigrator : ConfigFileMigrator<SearchHistory> {
    override val latestVersion = 1

    constructor() : super(SearchHistory::class.java, {})
}

class StreamChannelStateMigrator : ConfigFileMigrator<StreamChannelState> {
    override val latestVersion = 1

    constructor() : super(StreamChannelState::class.java, {})
}