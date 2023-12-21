package io.ktlab.kown

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktlab.kown.model.DownloadListener
import io.ktlab.kown.model.DownloadTaskBO
import io.ktlab.kown.model.DownloadTaskVO
import io.ktlab.kown.model.KownTaskStatus
import io.ktlab.kown.model.isPauseAble
import io.ktlab.kown.model.isProcessing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.timer

private val logger = KotlinLogging.logger { }

class KownDownloader(private val config: KownConfig) {
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler {
                        _,
                        throwable,
                    ->
                    throwable.printStackTrace()
                },
        )

    private val dbHelper = config.dbHelper
    private lateinit var taskQueueFlow: MutableStateFlow<List<DownloadTaskBO>>
    private val dispatcher = DownloadTaskDispatcher(config)

    private var downloadingCnt = 0
    private var lastDownloadingCnt = 0
    private val mutex = Mutex()

    private val onJobCompleteAction = {
        runBlockingWithLock {
            lastDownloadingCnt = downloadingCnt
            downloadingCnt--
        }
    }

    private val guard1 = DownloadTaskBO.Builder("guardTask", "", "", config).setTag("!!kown.guardTask").build()
    private val guard2 = DownloadTaskBO.Builder("guardTask", "", "", config).setTag("!!kown.guardTask").build()
    private var currentGuard = guard1

    private fun guardTask(): DownloadTaskBO {
        currentGuard = if (currentGuard == guard1) guard2 else guard1
        return currentGuard
    }

    init {
        runBlocking {
            dbScope.async {
                dbHelper.getAllDownloadTask().let { tasks ->
                    taskQueueFlow =
                        MutableStateFlow(
                            tasks +
                                currentGuard,
                        )
                }
            }.await()
            val job =
                scope.launch {
                    taskQueueFlow.collect {
                        mutex.lock()
                        if (downloadingCnt > config.concurrentDownloads) {
                            it.firstOrNull { it.status == KownTaskStatus.Running }?.let {
                                pauseTasks(listOf(it))
                            }
                        } else if (downloadingCnt < config.concurrentDownloads && it.any { it.status is KownTaskStatus.Queued }) {
                            val task = it.first { it.status is KownTaskStatus.Queued }
                            task.status = KownTaskStatus.Running
                            lastDownloadingCnt = downloadingCnt
                            downloadingCnt++
                            dispatcher.download(task, onJobCompleteAction)
                        }
                        mutex.unlock()
                    }
                }
            timer("kownSyncTask", false, 0L, 1000L) {
                if (job.isCompleted) {
                    cancel()
                }
                if (downloadingCnt != 0 || lastDownloadingCnt != 0) {
                    syncTask(taskQueueFlow.value)
                    // sync once after download finished
                    if (lastDownloadingCnt != 0 && downloadingCnt == 0) {
                        lastDownloadingCnt = 0
                    }
                    taskQueueFlow.tryEmit(
                        taskQueueFlow.value
                            .filter { it.tag != "!!kown.guardTask" } + guardTask(),
                    )
                }
            }
        }
    }

    fun getStatus(taskId: String): KownTaskStatus {
        return taskQueueFlow.value.firstOrNull { it.taskId == taskId }?.status ?: KownTaskStatus.Unknown
    }

    fun setMaxConcurrentDownloads(max: Int) {
        assert(max > 0) { "max must be greater than 0" }
        config.concurrentDownloads = max
    }

    private fun runBlockingWithLock(block: () -> Unit) =
        runBlocking {
            mutex.lock()
            block()
            mutex.unlock()
        }

    private fun blockingOpsById(
        taskId: String,
        block: (DownloadTaskBO) -> Unit,
    ) = runBlockingWithLock {
        taskQueueFlow.value.firstOrNull { it.taskId == taskId }?.let { block(it) }
    }

    private fun blockingOpsByTag(
        tag: String,
        block: (List<DownloadTaskBO>) -> Unit,
    ) = runBlockingWithLock {
        block(taskQueueFlow.value.filter { it.tag == tag })
    }

    private fun blockingOpsByTagMatched(
        match: (String?) -> Boolean,
        block: (List<DownloadTaskBO>) -> Unit,
    ) = runBlockingWithLock {
        block(taskQueueFlow.value.filter { match(it.tag) })
    }

    private fun blockingOpsAll(block: (List<DownloadTaskBO>) -> Unit) = runBlockingWithLock { block(taskQueueFlow.value) }

    /**
     * enqueue task. if task already exist, exception will be thrown
     * @param task task
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun enqueue(
        task: DownloadTaskBO,
        listener: DownloadListener = DownloadListener(),
    ) {
        // if task already exist, update it
        task.downloadListener = listener
        task.status = KownTaskStatus.Queued(task.status)
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
        val mappedTasks = tasks.map { it.apply { status = KownTaskStatus.Queued(status) } }
        dbScope.launch {
            dbHelper.batchInsert(mappedTasks)
        }
        taskQueueFlow.update { it + mappedTasks }
    }

    // batch enqueue task, all task will use same listener
    fun enqueue(
        tasks: List<DownloadTaskBO>,
        listener: DownloadListener? = null,
    ) {
        val mappedTasks =
            tasks.map {
                it.apply {
                    listener?.let { downloadListener = listener }
                    status = KownTaskStatus.Queued(status)
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
    fun retryById(
        taskId: String,
        listener: DownloadListener? = null,
    ) = blockingOpsById(taskId) {
        logger.debug { "retryById: $taskId" }
        retryTasks(listOf(it), listener)
    }

    fun retryByTag(
        tag: String,
        listener: DownloadListener? = null,
    ) = blockingOpsByTag(tag) {
        logger.debug { "retryByTag: $tag" }
        retryTasks(it, listener)
    }

    fun retryByTagMatched(
        match: (String?) -> Boolean,
        listener: DownloadListener? = null,
    ) = blockingOpsByTagMatched(match) {
        logger.debug { "retryByTagMatched: $match" }
        retryTasks(it, listener)
    }

    fun retryAll(listener: DownloadListener? = null) = blockingOpsAll { retryTasks(it, listener) }

    /**
     * cancel task by id. only [KownTaskStatus.Paused], [KownTaskStatus.Queued], [KownTaskStatus.Running], [KownTaskStatus.PostProcessing] task can be cancelled
     * @param taskId task id
     * @param listener listener,only non-null listener will be used
     */
    fun cancelById(taskId: String) =
        blockingOpsById(taskId) {
            logger.debug { "cancelById: $taskId" }
            cancelTasks(listOf(it))
        }

    fun cancelByTag(tag: String) =
        blockingOpsByTag(tag) {
            logger.debug { "cancelByTag: $tag" }
            cancelTasks(it)
        }

    fun cancelByTagMatched(match: (String?) -> Boolean) =
        blockingOpsByTagMatched(match) {
            logger.debug { "cancelByTagMatched: $match" }
            cancelTasks(it)
        }

    fun cancelAll() =
        blockingOpsAll {
            logger.debug { "cancelAll" }
            cancelTasks(it)
        }

    fun pauseById(taskId: String) =
        blockingOpsById(taskId) {
            logger.debug { "pauseById: $taskId" }
            pauseTasks(listOf(it))
        }

    fun pauseByTag(tag: String) =
        blockingOpsByTag(tag) {
            logger.debug { "pauseByTag: $tag" }
            pauseTasks(it)
        }

    fun pauseByTagMatched(match: (String?) -> Boolean) =
        blockingOpsByTagMatched(match) {
            logger.debug { "pauseByTagMatched: $match" }
            pauseTasks(it)
        }

    fun pauseAll() =
        blockingOpsAll {
            logger.debug { "pauseAll" }
            pauseTasks(it)
        }

    /**
     * resume task by id. only resume paused task
     * @param taskId task id
     * @param listener listener,only non-null listener will be used
     */
    fun resumeById(
        taskId: String,
        listener: DownloadListener? = null,
    ) = blockingOpsById(taskId) {
        logger.debug { "resumeById: $taskId" }
        resumeTasks(listOf(it), listener)
    }

    /**
     * batch resume task by tag. only resume paused task
     * @param tag task tag
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun resumeByTag(
        tag: String,
        listener: DownloadListener? = null,
    ) = blockingOpsByTag(tag) {
        logger.debug { "resumeByTag: $tag" }
        resumeTasks(it, listener)
    }

    fun resumeByTagMatched(
        match: (String?) -> Boolean,
        listener: DownloadListener? = null,
    ) = blockingOpsByTagMatched(match) {
        logger.debug { "resumeByTagMatched: $match" }
        resumeTasks(it, listener)
    }

    /**
     * batch resume task. only resume paused task. recommended to use [resumeById] or [resumeByTag] if possible
     * @param listener listener. null listener will be ignored. if not null,previous listener will be replaced and each resumed task will use this listener
     */
    fun resumeAll(listener: DownloadListener? = null) =
        blockingOpsAll {
            logger.debug { "resumeAll" }
            resumeTasks(it, listener)
        }

    fun removeById(taskId: String) =
        blockingOpsById(taskId) {
            logger.debug { "removeById: $taskId" }
            removeTasks(listOf(it))
        }

    fun removeByTag(tag: String) =
        blockingOpsByTag(tag) {
            logger.debug { "removeByTag: $tag" }
            removeTasks(it)
        }

    fun removeByTagMatched(match: (String?) -> Boolean) =
        blockingOpsByTagMatched(match) {
            logger.debug { "removeByTagMatched: $match" }
            removeTasks(it)
        }

    /**
     * batch remove task. only resume paused task. recommended to use [removeById] or [removeByTag] if possible
     */
    fun removeAll() =
        blockingOpsAll {
            logger.debug { "removeAll" }
            removeTasks(it)
        }

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

    private fun retryTasks(
        tasks: List<DownloadTaskBO>,
        listener: DownloadListener? = null,
    ) {
        val mappedTasks = tasks.filter { it.status is KownTaskStatus.Failed }.map { it.regenTask(listener) }
        dbScope.launch {
            dbHelper.batchUpdate(mappedTasks)
        }
        taskQueueFlow.update {
            val filtered = it.filter { it.taskId !in mappedTasks.map { it.taskId } }
            filtered + mappedTasks
        }
    }

    private fun resumeTasks(
        tasks: List<DownloadTaskBO>,
        listener: DownloadListener? = null,
    ) {
        val mappedTasks =
            tasks
                .filter { it.status is KownTaskStatus.Paused }
                .map {
                    it.apply {
                        listener?.let { downloadListener = listener }
                        status = KownTaskStatus.Queued(status)
                    }
                }
        dbScope.launch {
            dbHelper.batchUpdate(mappedTasks)
        }
        taskQueueFlow.update {
            val notUpdate = it.filter { it.taskId !in mappedTasks.map { it.taskId } }
            return@update notUpdate + mappedTasks
        }
    }

    private fun cancelTasks(tasks: List<DownloadTaskBO>) {
        val filteredTask =
            tasks
                .map {
                    it.also {
                        when (it.status) {
                            is KownTaskStatus.Queued -> {
                                it.status = KownTaskStatus.Failed("task cancelled", (it.status as KownTaskStatus.Queued).lastStatus)
                            }
                            is KownTaskStatus.Paused -> {
                                it.status = KownTaskStatus.Failed("task cancelled", (it.status as KownTaskStatus.Paused).lastStatus)
                            }
                            is KownTaskStatus.Running, is KownTaskStatus.PostProcessing -> {
                                dispatcher.cancel(it)
                            }
                        }
                    }
                }
                .filter { it.status is KownTaskStatus.Failed }
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
                        is KownTaskStatus.Queued -> {
                            it.status = KownTaskStatus.Paused((it.status as KownTaskStatus.Queued).lastStatus)
                        }
                        is KownTaskStatus.Running, is KownTaskStatus.PostProcessing -> {
                            // pause will cause task status change,and save to db manually in executor.
                            dispatcher.pause(it)
                        }
                    }
                }
            }
            .filter { it.status is KownTaskStatus.Paused }
            //
            .apply { syncTask(this) }
    }

    private fun removeTasks(tasks: List<DownloadTaskBO>) {
        val filteredTask = tasks.groupBy { it.status.isProcessing() }
        // for running task, cancel it first
        filteredTask[true]?.let {
            cancelTasks(it)
        }
        val waitingForDeleteTask = (filteredTask[false] ?: listOf()) + (filteredTask[true] ?: listOf())
        taskQueueFlow.update { it.filter { task -> waitingForDeleteTask.none { it.taskId == task.taskId } } }
        dbScope.launch {
            dbHelper.removeByTaskIds(waitingForDeleteTask.map { it.taskId })
        }
    }

    private fun syncTask(tasks: List<DownloadTaskBO>) {
        dbScope.launch {
            dbHelper.batchUpdate(tasks)
        }
    }

    fun getAllDownloadTaskFlow(): Flow<List<DownloadTaskVO>> =
        taskQueueFlow.map {
            logger.debug { "getAllDownloadTaskFlow" }
            it.map { DownloadTaskVO.fromBO(it) }
        }

    fun getAllDownloadTaskFlowByTagMatched(match: (String?) -> Boolean): Flow<List<DownloadTaskVO>> =
        taskQueueFlow.map {
            logger.debug { "getAllDownloadTaskFlow" }
            it.filter { match(it.tag) }.map { DownloadTaskVO.fromBO(it) }
        }

    fun newRequestBuilder(
        url: String,
        dirPath: String,
        filename: String,
    ): DownloadTaskBO.Builder {
        return DownloadTaskBO.Builder(url, dirPath, filename, config)
    }

    companion object {
        fun new(): KownBuilder {
            return KownBuilder()
        }
    }
}
