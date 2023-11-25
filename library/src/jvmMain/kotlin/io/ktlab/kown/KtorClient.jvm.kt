package io.ktlab.kown

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint

actual fun httpClient(clientConfig: HttpClientConfig<*>.() -> Unit) =
    HttpClient(CIO) {
        clientConfig(this)
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                requestTimeout = 0
                connectTimeout = 0
                connectAttempts = 5
            }
        }
    }
