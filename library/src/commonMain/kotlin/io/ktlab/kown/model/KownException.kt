package io.ktlab.kown.model

import kotlin.coroutines.cancellation.CancellationException

internal class DownloadException(
    private val reason: String,
    val lastStatus: KownTaskStatus,
) : IllegalStateException(reason)

internal class PauseException(
    private val reason: String,
    val lastStatus: KownTaskStatus,
) : CancellationException(reason)
