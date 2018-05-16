package shibafu.yukari.media2.impl

import com.sys1yagi.mastodon4j.api.entity.Attachment
import shibafu.yukari.media2.MemoizeMedia

class DonPicture(private val attachment: Attachment) : MemoizeMedia(attachment.remoteUrl ?: attachment.url) {

    override fun canPreview(): Boolean = true

    override fun resolveMediaUrl(): String = attachment.remoteUrl ?: attachment.url

    override fun resolveThumbnailUrl(): String = attachment.previewUrl
}