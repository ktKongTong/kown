package io.ktlab.kown.database

import io.ktlab.kown.model.DownloadTaskBO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object NoOpsDBHelper : DBHelper {
    override suspend fun find(id: String): DownloadTaskBO? = null

    override suspend fun insert(task: DownloadTaskBO) {}

    override suspend fun batchInsert(tasks: List<DownloadTaskBO>) {}

    override suspend fun update(task: DownloadTaskBO) {}

    override suspend fun batchUpdate(tasks: List<DownloadTaskBO>) {}

    override suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        lastModifiedAt: Long,
    ) {}

    override fun getAllDownloadTaskFlow(): Flow<List<DownloadTaskBO>> = emptyFlow()

    override suspend fun getAllDownloadTask(): List<DownloadTaskBO> = listOf()

    override suspend fun remove(id: String) {}

    override suspend fun removeAll() {}

    override suspend fun removeByTaskIds(ids: List<String>) {}

//    override suspend fun removeByTag(tag:String) {}
    override suspend fun removeByDays(days: Int) {}
}
