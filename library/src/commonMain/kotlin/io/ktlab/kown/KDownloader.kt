package io.ktlab.kown

import io.ktlab.kown.model.*
import io.ktlab.kown.model.isPauseAble
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.timer

class KownDownloader(private val config: KownConfig) {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { coroutineContext, throwable ->  throwable.printStackTrace() })

    private val dbHelper = config.dbHelper
    private lateinit var  taskQueueFlow: MutableStateFlow<List<DownloadTaskBO>>
    private val dispatcher = DownloadTaskDispatcher(config)

    private var downloadingCnt = 0
    private var lastDownloadingCnt = 0
    private val mutex = Mutex()

    private val onJobCompleteAction = {
        runBlockingWithLock{
            lastDownloadingCnt = downloadingCnt
            downloadingCnt--
        }
    }
    private val guard1 = DownloadTaskBO.Builder("guardTask", "", "", config).setTag("!!kown.guardTask").build()
    private val guard2 = DownloadTaskBO.Builder("guardTask", "", "", config).setTag("!!kown.guardTask").build()
    private var currentGuard = guard1
    private fun guardTask():DownloadTaskBO {
        currentGuard = if (currentGuard == guard1) guard2 else guard1
        return currentGuard
    }
    init {
        runBlocking {
            dbScope.async{
                dbHelper.getAllDownloadTask().let {tasks ->
                    taskQueueFlow = MutableStateFlow(tasks)
                }
            }.await()
            // 最后一次更新时有下载
            val job = scope.launch {
                taskQueueFlow.collect {
                        mutex.lock()
                        if (downloadingCnt > config.concurrentDownloads) {
                            // pause task when downloadingCnt > max
                            it.firstOrNull { it.status == TaskStatus.Running }?.let {
                                pauseTasks(listOf(it))
                            }
                        }else if (downloadingCnt < config.concurrentDownloads && it.any { it.status is TaskStatus.Queued }) {
                            val task = it.first { it.status is TaskStatus.Queued }
                            task.status = TaskStatus.Running
                            lastDownloadingCnt = downloadingCnt
                            downloadingCnt++
                            dispatcher.download(task,onJobCompleteAction)
                        }
                        mutex.unlock()
                    }
            }
            timer("kownSyncTask", false, 0L, 1000L) {
                if (job.isCompleted) {
                    cancel()
                }
                if (downloadingCnt != 0 || lastDownloadingCnt!=0) {
                    syncTask(taskQueueFlow.value)
                    // sync once after download finished
                    if (lastDownloadingCnt!=0 && downloadingCnt == 0) {
                        lastDownloadingCnt = 0
                    }
                    taskQueueFlow.tryEmit(taskQueueFlow.value.filter { it.tag != "!!kown.guardTask" } + guardTask() )
                }
            }
        }
    }

    fun  getStatus(taskId: String):TaskStatus {
        return taskQueueFlow.value.firstOrNull { it.taskId == taskId }?.status ?: TaskStatus.Unknown
    }

    fun setMaxConcurrentDownloads(max: Int) {
        assert(max > 0) { "max must be greater than 0" }
        config.concurrentDownloads = max
    }

    private fun runBlockingWithLock(block: () -> Unit) = runBlocking {
        mutex.lock()
        block()
        mutex.unlock()
    }
    private fun blockingOpsById(taskId: String, block : (DownloadTaskBO) -> Unit) = runBlockingWithLock {
        taskQueueFlow.value.firstOrNull { it.taskId == taskId }?.let { block(it) }
    }
    private fun blockingOpsByTag(tag: String, block : (List<DownloadTaskBO>) -> Unit) = runBlockingWithLock { block(taskQueueFlow.value.filter { it.tag == tag }) }
    private fun blockingOpsAll(block : (List<DownloadTaskBO>) -> Unit) = runBlockingWithLock { block(taskQueueFlow.value) }

    /**
     * enqueue task. if task already exist, exception will be thrown
     * @param task task
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun enqueue(task: DownloadTaskBO, listener: DownloadListener = DownloadListener()) {
        // if task already exist, update it
        task.downloadListener = listener
        task.status = TaskStatus.Queued(task.status)
        taskQueueFlow.value.filter { it.taskId == task.taskId }.firstOrNull()?.let {
            throw IllegalArgumentException("task already exist")
        }
        dbScope.launch {
            dbHelper.insert(task)
        }
        taskQueueFlow.update { it + task }
    }
    // batch enqueue task, each task can have different listener
    fun enqueue(tasks: List<DownloadTaskBO>) {
        val mappedTasks = tasks.map { it.apply {status = TaskStatus.Queued(status) } }
        dbScope.launch {
            dbHelper.batchInsert(mappedTasks)
        }
        taskQueueFlow.update { it + mappedTasks }
    }
    // batch enqueue task, all task will use same listener
    fun enqueue(tasks: List<DownloadTaskBO>, listener: DownloadListener? = null) {
        val mappedTasks = tasks.map {
            it.apply {
                listener?.let { downloadListener = listener }
                status = TaskStatus.Queued(status)
            }
        }
        dbScope.launch {
            dbHelper.batchInsert(mappedTasks)
        }
        taskQueueFlow.update { it + mappedTasks }
    }

    /**
     * retry task by id. only retry failed task.
     * @param taskId task id
     * @param listener listener,only non-null listener will be used
     */
    fun retryById(taskId: String, listener: DownloadListener? = null) = blockingOpsById(taskId) { retryTasks(listOf(it), listener) }
    fun retryByTag(tag: String, listener: DownloadListener? = null) = blockingOpsByTag(tag){ retryTasks(it, listener) }
    fun retryAll(listener: DownloadListener? = null) = blockingOpsAll { retryTasks(it, listener) }

    /**
     * cancel task by id. only [TaskStatus.Paused],[TaskStatus.Queued],[TaskStatus.Running],[TaskStatus.PostProcessing] task can be cancelled
     * @param taskId task id
     * @param listener listener,only non-null listener will be used
     */
    fun cancelById(taskId: String) = blockingOpsById(taskId) { cancelTasks(listOf(it)) }
    fun cancelByTag(tag: String)  = blockingOpsByTag(tag) { cancelTasks(it) }
    fun cancelAll() = blockingOpsAll { cancelTasks(it) }


    fun pauseById(taskId: String) = blockingOpsById(taskId) { pauseTasks(listOf(it)) }
    fun pauseByTag(tag: String) = blockingOpsByTag (tag) { pauseTasks(it) }
    fun pauseAll()  = blockingOpsAll { pauseTasks(it) }


    /**
     * resume task by id. only resume paused task
     * @param taskId task id
     * @param listener listener,only non-null listener will be used
     */
    fun resumeById(taskId: String, listener: DownloadListener? = null) = blockingOpsById(taskId) { resumeTasks(listOf(it), listener) }
    /**
     * batch resume task by tag. only resume paused task
     * @param tag task tag
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun resumeByTag(tag: String, listener: DownloadListener? = null) = blockingOpsByTag(tag) { resumeTasks(it, listener) }
    /**
     * batch resume task. only resume paused task. recommended to use [resumeById] or [resumeByTag] if possible
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun resumeAll(listener: DownloadListener? = null) = blockingOpsAll { resumeTasks(it, listener) }

    fun removeById(taskId: String) = blockingOpsById(taskId) { removeTasks(listOf(it)) }
    fun removeByTag(tag: String) = blockingOpsByTag(tag) { removeTasks(it) }
    /**
     * batch remove task. only resume paused task. recommended to use [removeById] or [removeByTag] if possible
     */
    fun removeAll() = blockingOpsAll { removeTasks(it) }

    fun clear() {
        runBlocking {
            cancelAll()
            mutex.lock()
            dbScope.async {
                dbHelper.removeAll()
            }.await()
            taskQueueFlow.update { listOf() }
            mutex.unlock()
        }
    }

    private fun retryTasks(tasks: List<DownloadTaskBO>, listener: DownloadListener? = null) {
        val mappedTasks = tasks.filter { it.status is TaskStatus.Failed }.map { it.regenTask(listener) }
        dbScope.launch {
            dbHelper.batchUpdate(mappedTasks)
        }
        taskQueueFlow.update {
            val filtered = it.filter { it.taskId !in mappedTasks.map { it.taskId } }
            filtered + mappedTasks
        }
    }

    private fun resumeTasks(tasks: List<DownloadTaskBO>, listener: DownloadListener? = null) {
        val mappedTasks = tasks
            .filter { it.status is TaskStatus.Paused }
            .map { it.apply {
                    listener?.let { downloadListener = listener }
                    status = TaskStatus.Queued(status)
                } }
        dbScope.launch {
            dbHelper.batchUpdate(mappedTasks)
        }
        taskQueueFlow.update {
            val notUpdate = it.filter { it.taskId !in mappedTasks.map { it.taskId } }
            return@update notUpdate + mappedTasks
        }
    }

    private fun cancelTasks(tasks: List<DownloadTaskBO>) {
        val filteredTask = tasks
            .map {
                it.also {
                    when (it.status) {
                        is TaskStatus.Queued -> {
                            it.status = TaskStatus.Failed("task cancelled", (it.status as TaskStatus.Queued).lastStatus)
                        }
                        is TaskStatus.Paused -> {
                            it.status = TaskStatus.Failed("task cancelled", (it.status as TaskStatus.Paused).lastStatus)
                        }
                        is TaskStatus.Running, is TaskStatus.PostProcessing -> { dispatcher.cancel(it) }
                    }
                }
            }
            .filter { it.status is TaskStatus.Failed }
        syncTask(filteredTask)
        taskQueueFlow.update {
            it.filter { task -> filteredTask.none { it.taskId == task.taskId } } + filteredTask
        }
    }

    private fun pauseTasks(tasks: List<DownloadTaskBO>) {
        tasks.filter { it.status.isPauseAble() }
            .map {
                it.apply {
                    when (it.status) {
                        is TaskStatus.Queued -> {
                            it.status = TaskStatus.Paused((it.status as TaskStatus.Queued).lastStatus)
                        }
                        is TaskStatus.Running, is TaskStatus.PostProcessing -> {
                            // pause will cause task status change,and save to db manually in executor.
                            dispatcher.pause(it)
                        }
                    }
                }
            }
            .filter { it.status is TaskStatus.Paused }
            //
            .apply { syncTask(this) }
    }


    private fun removeTasks(tasks: List<DownloadTaskBO>) {
        val filteredTask = tasks.groupBy { it.status.isProcessing() }
        // for running task, cancel it first
        filteredTask[true]?.let {
            cancelTasks(it)
        }
        val waitingForDeleteTask = (filteredTask[false]?: listOf()) + (filteredTask[true]?: listOf())
        taskQueueFlow.update { it.filter { task -> waitingForDeleteTask.none { it.taskId == task.taskId } } }
        dbScope.launch {
            dbHelper.removeByTaskIds(waitingForDeleteTask.map { it.taskId })
        }
    }

    private fun syncTask(tasks: List<DownloadTaskBO>) {
        dbScope.launch{
            dbHelper.batchUpdate(tasks.filter {  it.tag != "!!kown.guardTask"  })
        }
    }
    fun getAllDownloadTaskFlow(): Flow<List<DownloadTaskBO>> = taskQueueFlow.map {
        it.dropLast(1)
    }

    fun newRequestBuilder(url: String, dirPath: String, filename: String): DownloadTaskBO.Builder {
        return DownloadTaskBO.Builder(url, dirPath, filename,config)
    }

    companion object {
        fun new(): KownloaderBuilder {
            return KownloaderBuilder()
        }
    }
}

