package shibafu.yukari.twitter.statusimpl

import twitter4j.DirectMessage
import twitter4j.User

/**
 * 2018年以降の新しいDirectMessage APIでUser情報が不足しているのを補った互換クラス。
 */
class DirectMessageCompat(val message: DirectMessage,
                          private val _sender: User,
                          private val _recipient: User) : DirectMessage by message {

    override fun getRecipient(): User = _recipient

    override fun getRecipientScreenName(): String = _recipient.screenName

    override fun getSender(): User = _sender

    override fun getSenderScreenName(): String = _sender.screenName
}