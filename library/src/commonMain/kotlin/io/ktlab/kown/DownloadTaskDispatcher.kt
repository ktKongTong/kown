package io.ktlab.kown

import io.ktlab.kown.model.DownloadTaskBO
import io.ktlab.kown.model.PauseException
import io.ktlab.kown.model.TaskStatus
import io.ktlab.kown.model.isProcessing
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

// handle running/postProcessing task
class DownloadTaskDispatcher(
    private val config: KownConfig
) {
    private val scope = CoroutineScope(SupervisorJob() +  Dispatchers.IO + CoroutineExceptionHandler { coroutineContext, throwable ->  throwable.printStackTrace() })

    private val client = config.client?: ktorClient(config)
    private val mutex = Mutex()
    private val downloadingTaskExecutor = mutableMapOf<String, DownloadTaskExecutor>()

    fun download(task: DownloadTaskBO, onJobComplete: () -> Unit = {}) {
        val executor = DownloadTaskExecutor(task, config.dbHelper,client,config)
        runBlocking {
            mutex.lock()
            downloadingTaskExecutor[task.taskId] = executor
            mutex.unlock()
        }
        task.job = scope.launch {
            executor.run()
        }
        task.job.invokeOnCompletion {
            downloadingTaskExecutor.remove(task.taskId)
            onJobComplete()
        }
    }

    fun pause(task: DownloadTaskBO) {
        if (!task.status.isProcessing()) {
            return
        }
        runBlocking {
            mutex.lock()
            val executor = downloadingTaskExecutor[task.taskId]
            if (executor != null) {
                    downloadingTaskExecutor.remove(task.taskId)
                task.job.cancel(PauseException("task paused", task.status))
            }
            mutex.unlock()
        }
    }

    fun cancel(task: DownloadTaskBO) {
        if (task.status != TaskStatus.Running && task.status != TaskStatus.PostProcessing) {
            return
        }
        runBlocking {
            mutex.lock()
            val executor = downloadingTaskExecutor[task.taskId]
            if (executor != null) {
                downloadingTaskExecutor.remove(task.taskId)
                task.job.cancel()
            }
            mutex.unlock()
        }
    }

}