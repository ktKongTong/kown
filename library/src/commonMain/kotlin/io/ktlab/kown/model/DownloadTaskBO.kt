package io.ktlab.kown.model


import io.ktlab.kown.KownConfig
import io.ktlab.kown.DownloadTaskExecutor
import io.ktlab.kown.getUniqueId
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock

enum class RenameStrategy {
    DEFAULT,
    APPEND_INDEX
}

fun DownloadTaskBO.reset() {
    speed = 0
    downloadedBytes = 0
    estimatedTime = 0
}

class DownloadTaskVO(
    val taskId: String,
    val title: String,
    val tag: String?,
    val headers: Map<String, String>?,
    val status: TaskStatus,
    var url: String,
    val dirPath: String,
    val renameAble: Boolean,
    val renameStrategy: RenameStrategy,
    val filename: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val lastModifiedAt: Long,
    val createdAt: Long,
    val estimatedTime: Long,
    val speed: Long,
    val relateEntityId: String?,
) {
    fun fromBO(downloadTaskBO: DownloadTaskBO):DownloadTaskVO {
        return DownloadTaskVO(
            taskId = downloadTaskBO.taskId,
            title = downloadTaskBO.title,
            tag = downloadTaskBO.tag,
            headers = downloadTaskBO.headers,
            status = downloadTaskBO.status,
            url = downloadTaskBO.url,
            dirPath = downloadTaskBO.dirPath,
            renameAble = downloadTaskBO.renameAble,
            renameStrategy = downloadTaskBO.renameStrategy,
            filename = downloadTaskBO.filename,
            totalBytes = downloadTaskBO.totalBytes,
            downloadedBytes = downloadTaskBO.downloadedBytes,
            lastModifiedAt = downloadTaskBO.lastModifiedAt,
            createdAt = downloadTaskBO.createdAt,
            estimatedTime = downloadTaskBO.estimatedTime,
            speed = downloadTaskBO.speed,
            relateEntityId = downloadTaskBO.relateEntityId,
        )
    }
}


class DownloadTaskBO(
    val taskId: String,
    val title: String,
    val tag: String?,
    val headers: Map<String, String> = mapOf(),
    var status: TaskStatus = TaskStatus.Initial,
    var url: String = "",
    var dirPath: String = "",
    var renameAble: Boolean = false,
    var renameStrategy: RenameStrategy = RenameStrategy.DEFAULT,
    var filename: String = "",
    var totalBytes: Long = 0,
    var downloadedBytes: Long = 0,
    val relateEntityId: String? = null,
    internal var requestTimeout: Long = Long.MAX_VALUE,
    internal var connectTimeout: Long = Long.MAX_VALUE,
    internal var eTag: String = "",

    var estimatedTime: Long = 0,
    var speed: Long = 0,
    var lastModifiedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    internal var downloadListener: DownloadListener,
) {
    internal lateinit var job: Job


//    private val backupDownloadTaskBO: DownloadTaskBO by lazy { this.copy() }

     fun copyTask():DownloadTaskBO {
        return DownloadTaskBO(
            status = status,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            lastModifiedAt = lastModifiedAt,
            estimatedTime = estimatedTime,
            speed = speed,
            taskId = taskId,
            title = title,
            tag = tag,
            headers = headers,
            url = url,
            dirPath = dirPath,
            renameAble = renameAble,
            renameStrategy = renameStrategy,
            filename = filename,
            relateEntityId = relateEntityId,
            requestTimeout = requestTimeout,
            connectTimeout = connectTimeout,
            eTag = eTag,
            createdAt = createdAt,
            downloadListener = downloadListener,

        )
    }

    internal fun regenTask(listener: DownloadListener? = null):DownloadTaskBO {
//        val copied = this.copy(
//            status = TaskStatus.Queued(status),
//            downloadedBytes = 0,
//            lastModifiedAt = 0,
//            estimatedTime = 0,
//            createdAt = Clock.System.now().toEpochMilliseconds(),
//            speed = 0,
//        )
//        listener?.let { copied.downloadListener = it }

        return copyTask()
    }

    data class Builder(
        private val url: String,
        private val dirPath: String,
        private val filename: String,
        private val config: KownConfig
    ){
        private var tag: String? = null
        private var downloadListener: DownloadListener = DownloadListener()
        private var headers: Map<String, String> = mapOf()
        private var requestTimeout: Long = config.requestTimeout
        private var connectTimeout: Long = config.connectTimeout
        private var userAgent: String = config.userAgent
        private var relateEntityId : String? = null
        private var title: String = ""

        /**
         * set download request tag, could be used to operate all requests with the same tag.
         */
        fun setTag(tag: String) = apply {
            this.tag = tag
        }
        /**
         * set download request listener,usually for downloaded file postprocess or sync db stuff
         */
        fun setDownloadListener(downloadListener: DownloadListener) = apply {
            this.downloadListener = downloadListener
        }

        /**
         * set user agent for download request
         */
        fun setUserAgent(userAgent: String) = apply {
            this.userAgent = userAgent
        }
        /**
         * set download request headers if needed, some headers will be added automatically
         * see [DownloadTaskExecutor.run]
         */
        fun setHeaders(headers: Map<String, String>) = apply {
            this.headers = headers.let {
                if (it["User-Agent"].isNullOrEmpty() && userAgent.isNotEmpty()) {
                    it + ("User-Agent" to userAgent)
                } else { it }
            }
        }

        /**
         * set download request read timeout
         */
        fun setRequestTimeout(timeout: Long) = apply {
            this.requestTimeout = timeout
        }
        /**
         * set download request connect timeout
         */
        fun setConnectTimeout(timeout: Long) = apply {
            this.connectTimeout = timeout
        }

        fun setTitle(title: String) = apply {
            this.title = title
        }
        /**
         * set relate entity id for download request. this provides a way to link download task with other entity.
         * but this means you need to query db when downloading task status changed
         */
        fun setRelateEntityId(relateEntityId: String) = apply {
            this.relateEntityId = relateEntityId
        }
        fun build(): DownloadTaskBO {
            return DownloadTaskBO(
                taskId = getUniqueId(url, dirPath, filename),
                url = url,
                tag = tag,
                downloadListener = downloadListener,
                headers = headers,
                dirPath = dirPath,
                title = title.takeIf { it.isNotEmpty() } ?: filename,
                filename = filename,
                requestTimeout = requestTimeout,
                connectTimeout = connectTimeout,
                relateEntityId = relateEntityId,
            )
        }
    }
}