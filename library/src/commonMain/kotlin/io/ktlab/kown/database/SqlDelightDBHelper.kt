package io.ktlab.kown.database

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import io.ktlab.kown.model.DownloadListener
import io.ktlab.kown.model.DownloadTaskBO
import io.ktlab.kown.model.KownDatabase
import io.ktlab.kown.model.KownDownloadTaskModel
import io.ktlab.kown.model.KownTaskStatus
import io.ktlab.kown.model.RenameStrategy
import io.ktlab.kown.model.asString
import io.ktlab.kown.model.stringAsTaskStatusMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private fun KownDownloadTaskModel.convertToDownloadTaskBO(): DownloadTaskBO {
    return DownloadTaskBO(
        taskId = taskId,
        title = title,
        url = url,
        eTag = eTag,
        tag = tag,
        headers = headers ?: mapOf(),
        dirPath = dirPath,
        filename = filename,
        status = stringAsTaskStatusMapper(status),
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        lastModifiedAt = lastModifiedAt,
        renameAble = renameAble,
        createdAt = createAt,
        renameStrategy = RenameStrategy.valueOf(renameStrategy ?: "DEFAULT"),
        relateEntityId = relateEntityId,
        downloadListener = DownloadListener(),
    )
}

class SqlDelightDBHelper(private val driver: SqlDriver) : DBHelper {
    private var kownDatabase: KownDatabase

    private val stringOfStringMapAdapter =
        object : ColumnAdapter<Map<String, String>, String> {
            override fun decode(databaseValue: String): Map<String, String> {
                return Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), databaseValue)
            }

            override fun encode(value: Map<String, String>): String {
                return Json.encodeToJsonElement(MapSerializer(String.serializer(), String.serializer()), value).toString()
            }
        }

    init {
        KownDatabase.Schema.create(driver)
        kownDatabase =
            KownDatabase(
                driver = driver,
                KownDownloadTaskModelAdapter = KownDownloadTaskModel.Adapter(headersAdapter = stringOfStringMapAdapter),
            )
        runBlocking {
            syncOnStart()
        }
    }

    private suspend fun syncOnStart() {
        getAllDownloadTask().forEach {
            when (it.status) {
                KownTaskStatus.Running, is KownTaskStatus.Queued, KownTaskStatus.PostProcessing -> {
                    it.status = KownTaskStatus.Paused(it.status)
                    update(it)
                }
            }
        }
    }

    override suspend fun find(id: String): DownloadTaskBO? {
        return kownDatabase.kownModelQueries.selectByTaskId(id).executeAsOneOrNull()?.convertToDownloadTaskBO()
    }

    override suspend fun insert(task: DownloadTaskBO) {
        kownDatabase.kownModelQueries.insert(
            KownDownloadTaskModel(
                taskId = task.taskId,
                title = task.title,
                url = task.url,
                eTag = task.eTag,
                tag = task.tag,
                headers = task.headers,
                dirPath = task.dirPath,
                filename = task.filename,
                status = task.status.asString(),
                totalBytes = task.totalBytes,
                downloadedBytes = task.downloadedBytes,
                lastModifiedAt = Clock.System.now().toEpochMilliseconds(),
                createAt = task.createdAt,
                renameAble = task.renameAble,
                renameStrategy = task.renameStrategy.toString(),
                relateEntityId = task.relateEntityId,
            ),
        )
    }

    override suspend fun batchInsert(tasks: List<DownloadTaskBO>) {
        kownDatabase.transaction {
            tasks.forEach { task ->
                kownDatabase.kownModelQueries.insert(
                    KownDownloadTaskModel(
                        taskId = task.taskId,
                        title = task.title,
                        url = task.url,
                        eTag = task.eTag,
                        tag = task.tag,
                        headers = task.headers,
                        dirPath = task.dirPath,
                        filename = task.filename,
                        status = task.status.asString(),
                        totalBytes = task.totalBytes,
                        downloadedBytes = task.downloadedBytes,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds(),
                        createAt = task.createdAt,
                        renameAble = task.renameAble,
                        renameStrategy = task.renameStrategy.toString(),
                        relateEntityId = task.relateEntityId,
                    ),
                )
            }
        }
    }

    override suspend fun update(task: DownloadTaskBO) {
        task.lastModifiedAt = Clock.System.now().toEpochMilliseconds()
        insert(task)
    }

    override suspend fun batchUpdate(tasks: List<DownloadTaskBO>) {
        kownDatabase.transaction {
            tasks.forEach { task ->
                kownDatabase.kownModelQueries.insert(
                    KownDownloadTaskModel(
                        taskId = task.taskId,
                        title = task.title,
                        url = task.url,
                        eTag = task.eTag,
                        tag = task.tag,
                        headers = task.headers,
                        dirPath = task.dirPath,
                        filename = task.filename,
                        status = task.status.asString(),
                        totalBytes = task.totalBytes,
                        downloadedBytes = task.downloadedBytes,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds(),
                        createAt = task.createdAt,
                        renameAble = task.renameAble,
                        renameStrategy = task.renameStrategy.toString(),
                        relateEntityId = task.relateEntityId,
                    ),
                )
            }
        }
    }

    override suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        lastModifiedAt: Long,
    ) {
//        driver.execute(null, """
//            |UPDATE KownDownloadTaskModel SET downloadedBytes = ?1, lastModifiedAt = ?2 WHERE taskId = ?3
//          """.trimMargin(), 3) {
//            bindLong(1, downloadedBytes)
//            bindLong(2, lastModifiedAt)
//            bindString(3, id)
//        }
    }

    override suspend fun remove(id: String) {
        driver.execute(
            null,
            """
          |DELETE FROM KownDownloadTaskModel WHERE taskId = ?1
            """.trimMargin(),
            1,
        ) {
            bindString(1, id)
        }
    }

    override suspend fun getAllDownloadTask(): List<DownloadTaskBO> =
        kownDatabase.kownModelQueries.selectAll().executeAsList().map {
            it.convertToDownloadTaskBO()
        }

    override fun getAllDownloadTaskFlow(): Flow<List<DownloadTaskBO>> = TODO()

    override suspend fun removeByTaskIds(ids: List<String>) {
        kownDatabase.kownModelQueries.deleteByTaskIds(ids)
    }

    override suspend fun removeByDays(days: Int) {
        TODO()
    }

    override suspend fun removeAll() {
        kownDatabase.transaction {
            kownDatabase.kownModelQueries.deleteAll()
        }
    }
}
