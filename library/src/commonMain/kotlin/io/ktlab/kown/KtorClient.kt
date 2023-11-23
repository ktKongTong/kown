package io.ktlab.kown

import io.ktor.client.*
import io.ktor.client.plugins.*


fun ktorClient(config: KownConfig) :HttpClient = httpClient{
    followRedirects = true
    expectSuccess = false
    install(HttpTimeout){
        requestTimeoutMillis = config.requestTimeout
        connectTimeoutMillis = config.connectTimeout
    }
    if (config.retryCount > 0){
        install(HttpRequestRetry){
            retryOnException(config.retryCount, retryOnTimeout = true)
        }
    }

}

expect fun httpClient(clientConfig: HttpClientConfig<*>.() -> Unit = {}): HttpClient