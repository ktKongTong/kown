package io.ktlab.kown

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

actual fun httpClient(clientConfig: HttpClientConfig<*>.() -> Unit) =
    HttpClient(OkHttp) {
        clientConfig(this)

        engine {
            config {
                retryOnConnectionFailure(true)
                connectTimeout(0, TimeUnit.SECONDS)
            }
        }
    }
