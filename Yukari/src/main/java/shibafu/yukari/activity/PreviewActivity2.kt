package shibafu.yukari.activity

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.*
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.Status
import shibafu.yukari.media2.Media
import shibafu.yukari.media2.MediaFactory
import shibafu.yukari.service.TwitterService
import shibafu.yukari.service.TwitterServiceDelegate
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.StringUtil
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import shibafu.yukari.view.StatusView
import shibafu.yukari.view.TweetView
import twitter4j.Twitter
import twitter4j.TwitterException
import java.io.*
import java.util.*
import kotlin.coroutines.CoroutineContext

class PreviewActivity2 : ActionBarYukariBase(), CoroutineScope {
    // Collection
    private lateinit var collection: Array<Uri>

    // Pager
    private lateinit var viewPager: ViewPager
    private lateinit var adapter: PagerAdapter
    private var currentFragment: PreviewFragment? = null

    // Control panel
    private lateinit var llControlPanel: LinearLayout
    private lateinit var tweetView: TweetView
    private lateinit var ibBrowser: ImageButton
    private lateinit var ibSave: ImageButton
    private lateinit var ibRotateLeft: ImageButton
    private lateinit var ibRotateRight: ImageButton

    // QR code view
//    private lateinit var llQrText: LinearLayout
//    private lateinit var tvQrText: TextView

    // Animation
    private lateinit var fadeInAnimation: Animation
    private lateinit var fadeOutAnimation: Animation

    // Coroutine
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var isShowPanel: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                llControlPanel.startAnimation(fadeInAnimation)
                llControlPanel.visibility = View.VISIBLE
            } else {
                llControlPanel.startAnimation(fadeOutAnimation)
                llControlPanel.visibility = View.INVISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imagepreview2)

        val data = intent.data
        if (data == null) {
            showToast("null uri")
            finish()
            return
        } else if ("vine.co" == data.host || data.toString().contains("pbs.twimg.com/tweet_video/") || "video.twimg.com" == data.host) {
            val intent = Intent(Intent.ACTION_VIEW, data, this, MoviePreviewActivity::class.java)
            intent.putExtras(getIntent())
            startActivity(intent)
            finish()
            return
        }

        initializeField()

        val indexOfData = collection.indexOfFirst { it == data }
        val currentIndex = savedInstanceState?.getInt(STATE_CURRENT_INDEX, indexOfData) ?: indexOfData
        currentFragment = adapter.instantiateItem(viewPager, currentIndex) as PreviewFragment
        viewPager.setCurrentItem(currentIndex, false)
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) {}

            override fun onPageSelected(p0: Int) {
                currentFragment = adapter.instantiateItem(viewPager, p0) as PreviewFragment
            }

            override fun onPageScrollStateChanged(p0: Int) {}
        })

        ibRotateLeft.setOnClickListener {
            currentFragment?.rotateLeft()
        }

        ibRotateRight.setOnClickListener {
            currentFragment?.rotateRight()
        }

        ibBrowser.setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, collection[viewPager.currentItem]), null))
        }

        ibSave.setOnClickListener {
            doSave(collection[viewPager.currentItem])
        }

        // HTTP(S)ではない、あるいはDM添付画像の場合はブラウザ表示とダウンロードをサポートしない
        val mediaUrl = data.toString()
        if (!mediaUrl.startsWith("http") || isDMImage(mediaUrl)) {
            ibBrowser.isEnabled = false
            ibBrowser.visibility = View.GONE
            ibSave.visibility = View.GONE
        }

        // ツイートがExtraとして指定されている場合、それを表示する
        val anyStatus = intent.getSerializableExtra(EXTRA_STATUS)
        if (anyStatus is TwitterStatus) {
            tweetView.mode = StatusView.Mode.PREVIEW
            tweetView.status = anyStatus.originStatus
        } else {
            tweetView.visibility = View.GONE
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isShowPanel = savedInstanceState.getBoolean(STATE_IS_SHOW_PANEL, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_INDEX, viewPager.currentItem)
        outState.putBoolean(STATE_IS_SHOW_PANEL, isShowPanel)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun initializeField() {
        collection = collectionFromIntent()

        viewPager = findViewById(R.id.pager)
        llControlPanel = findViewById(R.id.llPreviewPanel)
        tweetView = findViewById(R.id.twvPreviewStatus)
        ibBrowser = findViewById(R.id.ibPreviewBrowser)
        ibSave = findViewById(R.id.ibPreviewSave)
        ibRotateLeft = findViewById(R.id.ibPreviewRotateLeft)
        ibRotateRight = findViewById(R.id.ibPreviewRotateRight)
//        llQrText = findViewById(R.id.llQrText)
//        tvQrText = findViewById(R.id.tvQrText)

        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.anim_fadein)
        fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.anim_fadeout)

        PagerAdapter(supportFragmentManager, collection, intent.getSerializableExtra(EXTRA_USER) as? AuthUserRecord).let {
            adapter = it
            viewPager.adapter = it
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectionFromIntent(): Array<Uri> {
        val collection = intent.getParcelableArrayExtra(EXTRA_COLLECTION)
        return if (collection.isNullOrEmpty()) {
            arrayOf(intent.data!!)
        } else {
            collection.map { it as Uri }.toTypedArray()
        }
    }

    private fun doSave(uri: Uri) = launch {
        val message = async(Dispatchers.IO) {
            val media = MediaFactory.newInstance(uri.toString()) ?: return@async "保存に失敗しました。"

            try {
                val dlm = applicationContext.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val request = media.downloadRequest ?: return@async "保存できない種類の画像です。"
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                val pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                pathExternalPublicDir.mkdirs()

                dlm.enqueue(request)
            } catch (e: IOException) {
                e.printStackTrace()
                return@async "保存に失敗しました。"
            }

            ""
        }.await()

        if (message.isNotEmpty()) {
            showToast(message)
        }
    }

    override fun allowAutoTheme(): Boolean = false

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    class PagerAdapter(fm: FragmentManager,
                       private val collection: Array<Uri>,
                       private val user: AuthUserRecord?) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = collection.size

        override fun getItem(position: Int): Fragment = PreviewFragment.newInstance(collection[position], user)
    }

    class PreviewFragment : Fragment(), CoroutineScope, TwitterServiceDelegate {
        // Arguments
        val uri: Uri
            get() = arguments!!.getParcelable(ARG_URI)!!
        val user: AuthUserRecord?
            get() = arguments!!.getSerializable(ARG_USER) as? AuthUserRecord

        // Image view
        private lateinit var imageView: SubsamplingScaleImageView

        // Progress
        private lateinit var progressBar: ProgressBar
        private lateinit var loadProgressText: TextView
        private lateinit var loadProgressText2: TextView

        // Coroutine
        private val job = Job()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_imagepreview, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            imageView = view.findViewById(R.id.ivPreviewImage)
            progressBar = view.findViewById(R.id.progressBar)
            loadProgressText = view.findViewById(R.id.tvPreviewProgress)
            loadProgressText2 = view.findViewById(R.id.tvPreviewProgress2)

            imageView.setMinimumDpi(80)
            imageView.setOnClickListener {
                val activity = requireActivity() as PreviewActivity2
                activity.isShowPanel = !activity.isShowPanel
            }
            imageView.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    (requireActivity() as PreviewActivity2).isShowPanel = false
                }

                override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                    (requireActivity() as PreviewActivity2).isShowPanel = false
                }
            })

            loadImage(savedInstanceState?.getSerializable(STATE_IMAGE_VIEW_STATE) as? ImageViewState)
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putSerializable(STATE_IMAGE_VIEW_STATE, imageView.state)
        }

        override fun onDestroy() {
            super.onDestroy()
            job.cancel()
        }

        fun rotateLeft() {
            val orientation = imageView.appliedOrientation
            ORIENTATIONS.reversed().forEach { o ->
                if (o < orientation) {
                    imageView.orientation = o
                    return
                }
            }
            // if orientation <= 0
            imageView.orientation = SubsamplingScaleImageView.ORIENTATION_270
        }

        fun rotateRight() {
            val orientation = imageView.appliedOrientation
            ORIENTATIONS.forEach { o ->
                if (o > orientation) {
                    imageView.orientation = o
                    return
                }
            }
            // if orientation >= 270
            imageView.orientation = SubsamplingScaleImageView.ORIENTATION_0
        }

        private fun loadImage(state: ImageViewState?) = launch {
            val context = requireContext()
            val url = uri.toString()

            val media = MediaFactory.newInstance(url)
            if (media == null) {
                showToast("画像の読み込みに失敗しました", Toast.LENGTH_LONG)
                return@launch
            }

            val file: File? = async(Dispatchers.IO) {
                // キャッシュディレクトリを取得
                val cacheDir: File = ensureCacheDirectory()

                // キャッシュファイル名を生成
                val fileKey = StringUtil.generateKey(url)
                val cacheFile = File(cacheDir, fileKey)

                // キャッシュディレクトリにファイルが無い場合、もしくはキャッシュが保存されてから
                // 1日以上経過している場合はダウンロードを行う
                // キャッシュディレクトリにファイルが無い場合、もしくはキャッシュが保存されてから
                // 1日以上経過している場合はダウンロードを行う
                if (!cacheFile.exists() || cacheFile.lastModified() < System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS) {
                    val input: InputStream
                    val beginTime: Long
                    var contentLength: Int = 0
                    var resolveInfo: Media.ResolveInfo? = null

                    // Open
                    if (url.startsWith("content://") || url.startsWith("file://")) {
                        try {
                            input = context.contentResolver.openInputStream(Uri.parse(url)) ?: return@async null
                            beginTime = System.currentTimeMillis()
                        } catch (e: FileNotFoundException) {
                            e.printStackTrace()
                            return@async null
                        }
                    } else if (isDMImage(url)) {
                        val service = getTwitterServiceAwait() ?: return@async null
                        input = try {
                            val twitter: Twitter = service.getTwitterOrThrow(user)
                            twitter.getDMImageAsStream(url)
                        } catch (e: TwitterException) {
                            e.printStackTrace()
                            return@async null
                        }
                        beginTime = System.currentTimeMillis()
                    } else {
                        try {
                            resolveInfo = media.resolveMedia()
                            contentLength = resolveInfo.contentLength
                            beginTime = System.currentTimeMillis()
                            input = resolveInfo.stream
                        } catch (e: IOException) {
                            e.printStackTrace()
                            return@async null
                        }
                    }

                    // Download
                    try {
                        val output = FileOutputStream(cacheFile)
                        val buf = ByteArray(4096)
                        var length: Int
                        var received: Int = 0
                        while (input.read(buf, 0, buf.size).also { length = it } != -1) {
                            output.write(buf, 0, length)
                            received += length
                            val currentTime = System.currentTimeMillis()
                            if (!isActive) {
                                output.close()
                                cacheFile.delete()
                                return@async null
                            } else {
                                updateProgress(received, contentLength, beginTime, currentTime)
                            }
                        }
                        output.close()
                        System.gc()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        return@async null
                    } finally {
                        try {
                            input.close()
                        } catch (ignore: IOException) {}
                        resolveInfo?.dispose()
                    }
                }

                return@async cacheFile
            }.await()

            progressBar.visibility = View.GONE
            loadProgressText.visibility = View.GONE
            loadProgressText2.visibility = View.GONE

            if (file == null || !file.exists()) {
                showToast("画像の読み込みに失敗しました", Toast.LENGTH_LONG)
                return@launch
            }

            if (state == null) {
                imageView.setImage(ImageSource.uri(file.absolutePath))
            } else {
                imageView.setImage(ImageSource.uri(file.absolutePath), state)
            }
        }

        private fun updateProgress(received: Int, length: Int, beginTime: Long, currentTime: Long) = launch(Dispatchers.Main) {
            val progress = received * 100 / length
            var elapsed = (currentTime - beginTime) / 1000
            if (elapsed < 1) {
               elapsed = 1
            }

            if (length < 1) {
                loadProgressText.text = ""
                loadProgressText2.text = String.format(Locale.US, "%d KB\n%dKB/s",
                        received / 1024,
                        (received / 1024) / elapsed)
            } else {
                loadProgressText.text = String.format(Locale.US, "%d%%", progress)
                loadProgressText2.text = String.format(Locale.US, "%d KB\n%dKB/s",
                        received / 1024,
                        (received / 1024) / elapsed)
            }
        }

        private fun ensureCacheDirectory(): File {
            val context = requireContext()

            var cacheDir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                context.externalCacheDir ?: context.cacheDir
            } else {
                context.cacheDir
            }

            cacheDir = File(cacheDir, "preview")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            return cacheDir
        }

        override fun getTwitterService(): TwitterService? = (requireActivity() as TwitterServiceDelegate).twitterService

        override fun isTwitterServiceBound(): Boolean = (requireActivity() as TwitterServiceDelegate).isTwitterServiceBound

        companion object {
            private const val ARG_URI = "uri"
            private const val ARG_USER = "user"

            private const val STATE_IMAGE_VIEW_STATE = "imageViewState"

            private val ORIENTATIONS = intArrayOf(
                    SubsamplingScaleImageView.ORIENTATION_0,
                    SubsamplingScaleImageView.ORIENTATION_90,
                    SubsamplingScaleImageView.ORIENTATION_180,
                    SubsamplingScaleImageView.ORIENTATION_270
            )

            fun newInstance(uri: Uri, user: AuthUserRecord? = null) : PreviewFragment {
                return PreviewFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_URI, uri)
                        putSerializable(ARG_USER, user)
                    }
                }
            }
        }
    }

    companion object {
        /** 画面に表示するStatus */
        private const val EXTRA_STATUS = "status"
        /** DM添付画像の取得に使用するUser */
        private const val EXTRA_USER = "user"
        /** 複数枚表示サポートで並べる画像のリスト */
        private const val EXTRA_COLLECTION = "collection"

        private const val STATE_CURRENT_INDEX = "currentIndex"
        private const val STATE_IS_SHOW_PANEL = "isShowPanel"

        @JvmStatic
        @JvmOverloads
        fun newIntent(context: Context, uri: Uri, status: Status? = null, user: AuthUserRecord? = null, collection: List<Uri> = emptyList()): Intent {
            return Intent(context, PreviewActivity2::class.java).apply {
                data = uri
                putExtra(EXTRA_COLLECTION, collection.toTypedArray())
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_USER, user)
            }
        }

        private fun isDMImage(url: String): Boolean {
            return url.startsWith("https://ton.twitter.com/") || url.contains("twitter.com/messages/media/")
        }
    }
}