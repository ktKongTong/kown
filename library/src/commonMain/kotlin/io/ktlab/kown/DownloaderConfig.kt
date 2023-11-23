package io.ktlab.kown

import io.ktlab.kown.database.DBHelper
import io.ktlab.kown.database.NoOpsDBHelper
import io.ktor.client.*
import jdk.jfr.Enabled

data class KownConfig(
    var databaseEnabled: Boolean = false,
    var timeoutEnabled: Boolean = false,
    var retryCount: Int = 0,
    var connectTimeout: Long = Long.MAX_VALUE,
    var requestTimeout: Long = Long.MAX_VALUE,

//    var chunkSize: Int = 4.MB,
    var concurrentDownloads: Int = 5,
    var dbHelper: DBHelper = NoOpsDBHelper,
    var client: HttpClient? = null,
    var userAgent: String = Constants.DEFAULT_USER_AGENT
)