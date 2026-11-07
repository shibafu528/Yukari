package shibafu.yukari.util

import kotlinx.coroutines.delay
import shibafu.yukari.service.TwitterService
import shibafu.yukari.service.TwitterServiceDelegate

suspend fun TwitterServiceDelegate.getTwitterServiceAwait(): TwitterService? {
    while (!isTwitterServiceBound) {
        delay(100)
    }

    return twitterService
}