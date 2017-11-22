package shibafu.yukari.mastodon.entity

import com.sys1yagi.mastodon4j.api.entity.Status
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.entity.Status as IStatus

// TODO: ビルド通すためだけにabstractになってる
abstract class DonStatus(val status: Status, receivedUser: AuthUserRecord) : IStatus