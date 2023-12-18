package io.ktlab.kown

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktlab.kown.database.DBHelper
import io.ktlab.kown.model.DownloadException
import io.ktlab.kown.model.DownloadTaskBO
import io.ktlab.kown.model.KownTaskStatus
import io.ktlab.kown.model.reset
import io.ktlab.kown.model.stringAsTaskStatusMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.FileHandle
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

private val logger = KotlinLogging.logger {}

class DownloadTaskExecutor(
    private val task: DownloadTaskBO,
    private val dbHelper: DBHelper,
    private val client: HttpClient,
    private val config: KownConfig,
) {
    private var responseCode: HttpStatusCode? = null
    private lateinit var tempPath: Path
    private lateinit var fileHandle: FileHandle

    private var isResumeSupported = false

    private var lastTrySyncTime = 0L
    private var lastTrySyncBytes = 0L
    private var lastSyncTime: Long = 0
    private var lastSyncBytes: Long = 0

    private var eTag: String = ""

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

//    suspend inline fun run(
//        crossinline onStart: () -> Unit = {},
//        crossinline onProgress: (value: Int) -> Unit = { _ -> },
//        crossinline onError: (error: String) -> Unit = { _ -> },
//        crossinline onCompleted: () -> Unit = {},
//        crossinline onPause: () -> Unit = {}
//    ) = run(
//        object : DownloadListener {
//        override fun onStart() = onStart()
//
//        override fun onProgress(value: Int) = onProgress(value)
//
//        override fun onError(error: String) = onError(error)
//
//        override fun onCompleted() = onCompleted()
//
//        override fun onPause() = onPause()
//    }
//    )

    suspend fun run() =
        withContext(Dispatchers.IO) {
            try {
                tempPath = getTempPath(task.dirPath, task.filename)
                checkIfFileExistAndRename()
                task.status = KownTaskStatus.Running
                task.downloadListener.onStart(task)
                val req =
                    client.prepareGet(task.url) {
                        header(HttpHeaders.IfRange, task.eTag)
                        header(HttpHeaders.Range, "bytes=${task.downloadedBytes}-")
                        task.headers.onEach { (key, value) -> header(key, value) }
                        timeout {
                            requestTimeoutMillis = task.requestTimeout
                            connectTimeoutMillis = task.connectTimeout
                        }
                    }
                req.execute { resp ->
                    responseCode = resp.status
                    eTag = resp.headers[HttpHeaders.ETag] ?: ""
                    checkIfFreshStartRequiredAndStart()
                    if (!resp.status.isSuccess()) {
                        throw DownloadException("Unsuccessful response code: $responseCode", task.status)
                    }
                    task.eTag = eTag
                    setIfPartialContentAndContentLength(resp)
                    fileHandle = createFileIfNotExist(tempPath)
                    var sink = fileHandle.sink().buffer()
                    if (isResumeSupported && task.downloadedBytes != 0L) {
                        sink = fileHandle.sink(task.downloadedBytes).buffer()
                    }
                    val channel = resp.bodyAsChannel()
                    sink.use {
                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining(8L.KB)
                            while (packet.isNotEmpty) {
                                task.downloadedBytes += packet.remaining
                                sink.write(packet.readBytes())
                            }
                            syncIfRequired()
                        }
                    }
                }
                task.downloadedBytes = task.totalBytes
                val path = getPath(task.dirPath, task.filename)
                FileSystem.SYSTEM.atomicMove(tempPath, path)
                task.status = KownTaskStatus.PostProcessing
                task.downloadListener.onCompleted(task)
                task.status = KownTaskStatus.Completed
                dbScope.launch { dbHelper.update(task) }
            } catch (e: CancellationException) {
                if (e.message?.startsWith("task.paused") == true) {
                    // seem that pass exception with cancellation exception is not implemented yet in jvm
                    // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines.cancellation/-cancellation-exception/
                    logger.debug { "task paused while executing: ${task.taskId}" }
                    val lastStatus = stringAsTaskStatusMapper(e.message!!.replace("task.paused.", ""))
                    task.status = KownTaskStatus.Paused(lastStatus)
                    dbScope.launch { dbHelper.update(task) }
                    task.downloadListener.onPaused(task)
                } else {
                    logger.debug { "task canceled while running: ${task.taskId}" }
                    deleteTempFile()
                    task.status = KownTaskStatus.Failed(e.message ?: "", task.status)
                    dbScope.launch { dbHelper.update(task) }
                    task.downloadListener.onCancelled(task)
                }
            } catch (e: Exception) {
                if (!isResumeSupported) {
                    deleteTempFile()
                    task.reset()
                }
                task.status = KownTaskStatus.Failed(e.message ?: "", task.status)
                dbScope.launch { dbHelper.update(task) }
                task.downloadListener.onError(task, e)
            } finally {
                if (::fileHandle.isInitialized) {
                    fileHandle.close()
                }
            }
        }

    private fun setIfPartialContentAndContentLength(resp: HttpResponse) {
        if (resp.status == HttpStatusCode.PartialContent) {
            isResumeSupported = true
            task.totalBytes = (resp.headers[HttpHeaders.ContentRange]?.split("/")?.last()?.toLong() ?: 0)
        } else {
            deleteTempFile()
            task.reset()
            resp.contentLength().let {
                task.totalBytes = it ?: task.totalBytes
            }
        }
    }

    private fun checkIfFileExistAndRename() {
        if (FileSystem.SYSTEM.exists(tempPath)) {
            if (!deleteTempFile()) {
                val path = tempPath.toString()
                tempPath = (path.split(".")[0] + "2." + path.split(".", limit = 2)[1]).toPath()
            }
        } else {
            task.reset()
        }
    }

    private fun createFileIfNotExist(tempPath: Path): FileHandle {
        if (!FileSystem.SYSTEM.exists(tempPath)) {
            if (tempPath.parent != null && !FileSystem.SYSTEM.exists(tempPath.parent!!)) {
                FileSystem.SYSTEM.createDirectories(tempPath.parent!!)
            }
        }
        return FileSystem.SYSTEM.openReadWrite(tempPath)
    }

    private fun deleteTempFile(): Boolean {
        val fs = FileSystem.SYSTEM
        if (fs.exists(tempPath)) {
            fs.delete(tempPath)
        }
        return true
    }

    private fun checkIfFreshStartRequiredAndStart() {
        if (responseCode == HttpStatusCode.RequestedRangeNotSatisfiable || isETagChanged(task)) {
            deleteTempFile()
            task.downloadedBytes = 0
            task.totalBytes = 0
        }
    }

    private fun isETagChanged(task: DownloadTaskBO): Boolean {
        return (!(eTag.isEmpty() || task.eTag.isEmpty()) && task.eTag != eTag)
    }

    private fun syncIfRequired() {
        val currentBytes: Long = task.downloadedBytes
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val bytesDelta: Long = currentBytes - lastSyncBytes
        val timeDelta: Long = currentTime - lastSyncTime
        val syncTimeDelta = currentTime - lastTrySyncTime
        val syncBytesDelta = currentBytes - lastTrySyncBytes
        if (syncTimeDelta > 300) {
            lastTrySyncBytes = currentBytes
            val speedInBPS: Long = (syncBytesDelta * 1000 / (syncTimeDelta + 1))
            lastTrySyncTime = currentTime
            task.speed = (speedInBPS)
            task.estimatedTime = (if (speedInBPS > 0) (task.totalBytes - currentBytes) / speedInBPS else 0)
        }

        if (bytesDelta > MIN_BYTES_FOR_SYNC && timeDelta > TIME_GAP_FOR_SYNC) {
            lastSyncBytes = currentBytes
            lastSyncTime = currentTime
            var progress = 0f
            if (task.totalBytes > 0) {
                progress = ((task.downloadedBytes * 100f) / task.totalBytes)
            }
            dbScope.launch {
                dbHelper.update(task)
            }
            task.downloadListener.onProgress(progress)
        }
    }

    companion object {
        private const val TIME_GAP_FOR_SYNC: Long = 300
        private const val MIN_BYTES_FOR_SYNC: Long = 65536
    }
}
