package io.ktlab.kown

import app.cash.sqldelight.db.SqlDriver
import io.ktlab.kown.database.DBHelper
import io.ktor.client.HttpClient

class KownBuilder {
    private var kownConfig = KownConfig()

    fun setDatabaseEnabled(enabled: Boolean) =
        apply {
            kownConfig.databaseEnabled = enabled
        }

    fun setDataBaseDriver(driver: SqlDriver) =
        apply {
            kownConfig.dbHelper = DBHelper.create(driver)
        }

    fun setRetryCount(count: Int = 0) =
        apply {
            kownConfig.retryCount = count
        }

//    fun setChunkSize(size: Int) = apply {
//        kownConfig.chunkSize = size
//    }

    fun setMaxConcurrentDownloads(count: Int = 5) =
        apply {
            kownConfig.concurrentDownloads = count
        }

    fun setClient(client: HttpClient) =
        apply {
            kownConfig.client = client
        }

    fun setUserAgent(userAgent: String) =
        apply {
            kownConfig.userAgent = userAgent
        }

    fun build(): KownDownloader {
        return KownDownloader(kownConfig)
    }
}
