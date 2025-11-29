package shibafu.yukari.common.imageloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.annotation.NonUiContext
import androidx.core.os.HandlerCompat
import androidx.preference.PreferenceManager
import com.google.common.util.concurrent.ThreadFactoryBuilder
import shibafu.yukari.R
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.BitmapCache.CacheKey
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.User
import shibafu.yukari.media2.Media
import shibafu.yukari.util.BitmapUtil
import java.io.File
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

object ImageLoader {
    fun load(@NonUiContext context: Context, url: String) = ImageLoaderTaskBuilder(context, url)

    fun load(@NonUiContext context: Context, media: Media) = ImageLoaderTaskBuilder(context, media)

    fun loadProfileIcon(@NonUiContext context: Context, user: User, preferBiggerImage: Boolean) = when {
        preferBiggerImage -> load(context, user.biggerProfileImageUrl)
        else -> load(context, user.profileImageUrl)
    }.setCacheKey(user.url!!).setCacheGroup(BitmapCache.PROFILE_ICON_CACHE)

    fun loadProfileIcon(@NonUiContext context: Context, user: AuthUserRecord) =
        load(context, user.ProfileImageUrl).setCacheKey(user.Url).setCacheGroup(BitmapCache.PROFILE_ICON_CACHE)
}

enum class ResolveMode {
    /** [Media.resolveMedia] を使って画像を取得 */
    MEDIA,
    /** [Media.resolveThumbnail] を使って画像を取得 */
    THUMBNAIL,
}

/**
 * [ImageLoaderTask] の結果を受け取るためのコールバック。Java互換用。
 */
interface ImageLoaderResultCallback<T> {
    fun onSuccess(value: T)
    fun onFailure(e: Throwable)
}

class ImageLoaderTaskBuilder {
    @NonUiContext private val context: Context
    private val url: String
    private val media: Media?
    private var resolveMode: ResolveMode = ResolveMode.MEDIA
    @CacheKey private var cacheGroup: String = BitmapCache.IMAGE_CACHE
    private var cacheKey: String
    private var mosaic: Boolean = false
    private var onQueue: (() -> Unit)? = null
    private var onDownloadStart: (() -> Unit)? = null

    constructor(@NonUiContext context: Context, url: String) {
        this.context = context.applicationContext
        this.url = url
        this.media = null
        this.cacheKey = url
    }

    constructor(@NonUiContext context: Context, media: Media) {
        this.context = context.applicationContext
        this.url = media.browseUrl
        this.media = media
        this.cacheKey = media.browseUrl
    }

    // setter

    /**
     * [Media]をどのように解決するかを指定
     */
    fun setResolveMode(resolveMode: ResolveMode) = also { it.resolveMode = resolveMode }

    /**
     * 容量クォータを共有するグループ名を指定 ([BitmapCache]クラスでは _cacheKey_ と呼ばれているもの)
     */
    fun setCacheGroup(@CacheKey cacheGroup: String) = also { it.cacheGroup = cacheGroup }

    /**
     * キャッシュを読み書きする際に使うキーを指定 ([BitmapCache]クラスでは _key_ と呼ばれているもの)
     */
    fun setCacheKey(cacheKey: String) = also { it.cacheKey = cacheKey }

    /**
     * モザイクフィルタを適用する
     */
    fun setMosaic(mosaic: Boolean) = also { it.mosaic = mosaic }

    // optional lifecycle callback

    fun onQueue(onQueue: () -> Unit) = also { it.onQueue = onQueue }

    fun onDownloadStart(onDownloadStart: (() -> Unit)) = also { it.onDownloadStart = onDownloadStart }

    // sink

    /**
     * 読み込みを開始し、結果を [Bitmap] として受け取る
     */
    fun asBitmap(onFinish: (result: Result<Bitmap>) -> Unit) {
        // TODO: nullableにするかどうか迷いがある。受け入れる理由はないが、Javaから呼ばれるので...
        if (url == null) {
            onFinish(Result.failure(IllegalArgumentException("resource is null")))
            return
        }

        val cache = when (mosaic) {
            true -> BitmapCache.getImageFromMemory(mosaicCacheKey(cacheKey), cacheGroup)
            false -> BitmapCache.getImageFromMemory(cacheKey, cacheGroup)
        }
        if (cache != null && !cache.isRecycled) {
            onFinish(Result.success(cache))
            return
        }

        onQueue?.invoke()
        TaskManager.subscribe(ImageLoaderTask(context, url, media, resolveMode, cacheGroup, cacheKey, mosaic, onDownloadStart, onFinish))
    }

    fun asBitmap(onFinish: ImageLoaderResultCallback<Bitmap>) {
        asBitmap { result -> result.onSuccess(onFinish::onSuccess).onFailure(onFinish::onFailure) }
    }

    /**
     * 読み込みを開始し、結果を [Drawable] として受け取る
     */
    fun asDrawable(onFinish: (result: Result<Drawable>) -> Unit) {
        asBitmap { onFinish(it.map(::bitmapToDrawable)) }
    }

    fun asDrawable(onFinish: ImageLoaderResultCallback<Drawable>) {
        asBitmap { result -> result.onSuccess(::bitmapToDrawable).onFailure(onFinish::onFailure) }
    }

    private fun bitmapToDrawable(bitmap: Bitmap) = BitmapDrawable(context.resources, bitmap)

    /**
     * 読み込みを開始し、結果を引数で指定した [ImageView] にセットする
     *
     * このメソッドを使う場合、[onQueue] および [onDownloadStart] で設定したコールバックは無視される。
     */
    fun toImageView(imageView: ImageView) {
        imageView.tag = cacheKey
        toImageView(WeakReference(imageView)) // ImageViewのリークを回避するために弱参照化
    }

    private fun toImageView(imageViewRef: WeakReference<ImageView>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        onQueue {
            val iv = imageViewRef.get() ?: return@onQueue
            if (cacheKey == iv.tag) {
                iv.setImageResource(R.drawable.yukatterload)
            }
        }
        onDownloadStart {
            val iv = imageViewRef.get() ?: return@onDownloadStart
            if (cacheKey == iv.tag && sp.getBoolean("pref_indicate_loading_from_remote", false)) {
                iv.setImageResource(R.drawable.loading_from_remote)
            }
        }
        asBitmap { result ->
            val iv = imageViewRef.get() ?: return@asBitmap
            result.onSuccess { bitmap ->
                if (cacheKey == iv.tag) {
                    iv.setImageBitmap(bitmap)
                }
            }.onFailure {
                it.printStackTrace()
                iv.setImageResource(R.drawable.ic_states_warning)
            }
        }
    }
}

class ImageLoaderTask(
    @NonUiContext private val context: Context,
    private val url: String,
    private val media: Media?,
    private val resolveMode: ResolveMode,
    @CacheKey private val cacheGroup: String,
    private val cacheKey: String,
    private val mosaic: Boolean,
    internal val onDownloadStart: (() -> Unit)?,
    internal val onFinish: (result: Result<Bitmap>) -> Unit,
) : Runnable {
    constructor(
        copyFrom: ImageLoaderTask,
        onDownloadStart: (() -> Unit)?,
        onFinish: (result: Result<Bitmap>) -> Unit
    ) : this(
        copyFrom.context,
        copyFrom.url,
        copyFrom.media,
        copyFrom.resolveMode,
        copyFrom.cacheGroup,
        copyFrom.cacheKey,
        copyFrom.mosaic,
        onDownloadStart,
        onFinish
    )

    val key = TaskKey(cacheGroup, cacheKey, mosaic)

    override fun run() {
        try {
            val image = load()
            mainThreadHandler.post { onFinish(Result.success(image)) }
        } catch (e: Exception) {
            mainThreadHandler.post { onFinish(Result.failure(e)) }
        }
    }

    private fun load(): Bitmap {
        val image = BitmapCache.getImageFromDisk(cacheKey, cacheGroup, context) ?: fetch() ?: throw NullBitmapException()
        if (mosaic) {
            val mosaicImage = BitmapUtil.createMosaic(image)
            BitmapCache.putImage(
                mosaicCacheKey(cacheKey),
                mosaicImage,
                context,
                cacheGroup,
                true,
                false
            )
            return mosaicImage
        }
        return image
    }

    private fun fetch(): Bitmap? {
        onDownloadStart?.let { callback -> mainThreadHandler.post(callback) }

        Log.d("ImageLoaderTask", "[download] ${key.hashCode()} $cacheGroup m=$mosaic : $cacheKey")

        var resolveInfo: Media.ResolveInfo? = null
        val inputStream = if (media == null) {
            URL(url).openStream()
        } else {
            resolveInfo = when (resolveMode) {
                ResolveMode.MEDIA -> media.resolveMedia()
                ResolveMode.THUMBNAIL -> media.resolveThumbnail()
            }
            if (resolveInfo == null) {
                throw FileNotFoundException("Resolve failed: ${media.browseUrl}")
            }
            resolveInfo.stream
        }

        val tempFile = File.createTempFile("image", ".tmp", context.externalCacheDir)
        try {
            inputStream.buffered().use { bis ->
                tempFile.outputStream().use { fos ->
                    bis.copyTo(fos)
                }
            }

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(tempFile.absolutePath, options)
            options.inSampleSize = max(options.outWidth / 512, options.outHeight / 512)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val image = BitmapFactory.decodeFile(tempFile.absolutePath, options)
            if (image != null) {
                BitmapCache.putImage(cacheKey, image, context, cacheGroup, !mosaic, true)
            }

            Log.d("ImageLoaderTask", "[finish  ] ${key.hashCode()} $cacheGroup m=$mosaic : $cacheKey")

            return image
        } finally {
            tempFile.delete()
            resolveInfo?.dispose()
        }
    }

    companion object {
        private val numberOfCores = Runtime.getRuntime().availableProcessors()

        /** 通常使用するExecutor */
        val imageExecutor: ExecutorService = Executors.newFixedThreadPool(numberOfCores, ThreadFactoryBuilder().setNameFormat("ImageLoader-i%d").build())
        /** プロフィール画像取得タスク専用のExecutor */
        val profileIconExecutor: ExecutorService = Executors.newFixedThreadPool(4, ThreadFactoryBuilder().setNameFormat("ImageLoader-p%d").build())

        private val mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    }
}

/**
 * ダウンロード、またはキャッシュから取得した画像を [BitmapFactory] でデコードした結果、nullが返された時に発生する例外
 */
class NullBitmapException : Exception("BitmapFactory returned null reference")

private fun mosaicCacheKey(key: String) = "[mosaic]$key"