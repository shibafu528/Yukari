package shibafu.yukari.common.imageloader

import android.graphics.Bitmap
import shibafu.yukari.common.bitmapcache.BitmapCache
import shibafu.yukari.common.bitmapcache.BitmapCache.CacheKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object TaskManager {
    private val tasks = ConcurrentHashMap<TaskKey, TaskState>()
    private val evictExecutor = Executors.newSingleThreadScheduledExecutor()

    fun subscribe(task: ImageLoaderTask) {
        tasks.getOrPut(task.key) { TaskState(task) }.subscribe(task)
    }

    fun scheduleEvict(key: TaskKey) {
        evictExecutor.schedule({ tasks.remove(key) }, 5, TimeUnit.SECONDS)
    }
}

data class TaskKey(@CacheKey val cacheGroup: String, val cacheKey: String, val mosaic: Boolean)

class TaskState(task: ImageLoaderTask) {
    private var isDownloadStarted = false
    private var result: Result<Bitmap>? = null

    private val onDownloadStartListeners = arrayListOf<(() -> Unit)?>()
    private val onFinishListeners = arrayListOf<(Result<Bitmap>) -> Unit>()

    init {
        val key = task.key
        val newTask = ImageLoaderTask(
            task,
            onDownloadStart = {
                synchronized(onDownloadStartListeners) {
                    isDownloadStarted = true
                    onDownloadStartListeners.forEach { it?.invoke() }
                }
            },
            onFinish = { result ->
                synchronized(onFinishListeners) {
                    this.result = result
                    onFinishListeners.forEach { it.invoke(result) }
                }
                TaskManager.scheduleEvict(key)
            },
        )
        if (key.cacheGroup == BitmapCache.PROFILE_ICON_CACHE) {
            ImageLoaderTask.profileIconExecutor.execute(newTask)
        } else {
            ImageLoaderTask.imageExecutor.execute(newTask)
        }
    }

    fun subscribe(task: ImageLoaderTask) {
        synchronized(onDownloadStartListeners) {
            if (isDownloadStarted) {
                task.onDownloadStart?.invoke()
            } else {
                onDownloadStartListeners.add(task.onDownloadStart)
            }
        }
        synchronized(onFinishListeners) {
            val result = result
            if (result != null) {
                task.onFinish(result)
            } else {
                onFinishListeners.add(task.onFinish)
            }
        }
    }
}