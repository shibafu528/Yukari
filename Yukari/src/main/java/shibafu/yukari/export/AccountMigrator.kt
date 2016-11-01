package shibafu.yukari.export

import shibafu.yukari.twitter.AuthUserRecord

/**
 * Created by shibafu on 2016/03/21.
 */
class AccountMigrator : ConfigFileMigrator<AuthUserRecord> {
    override val latestVersion: Int = 1

    constructor() : super(AuthUserRecord::class.java, {
//        version(1) {}
    })
}