package io.ktlab.kown.model

interface KownTaskStatus {
    data object Initial : KownTaskStatus

    data class Queued(val lastStatus: KownTaskStatus) : KownTaskStatus

    data object Running : KownTaskStatus

    data object PostProcessing : KownTaskStatus

    data class Paused(val lastStatus: KownTaskStatus) : KownTaskStatus

    data object Completed : KownTaskStatus

    data object Cancelled : KownTaskStatus

    data class Failed(val reason: String, val lastStatus: KownTaskStatus) : KownTaskStatus

    data object Unknown : KownTaskStatus
}

fun KownTaskStatus.isProcessing(): Boolean {
    return this == KownTaskStatus.Running || this == KownTaskStatus.PostProcessing
}

fun KownTaskStatus.isWaiting(): Boolean {
    return this is KownTaskStatus.Queued
}

fun KownTaskStatus.isSuccessFinite(): Boolean {
    return this == KownTaskStatus.Completed || this == KownTaskStatus.Cancelled
}

fun KownTaskStatus.isFinite(): Boolean {
    return this == KownTaskStatus.Completed || this == KownTaskStatus.Cancelled ||
        this is KownTaskStatus.Failed || this is KownTaskStatus.Paused
}

internal fun KownTaskStatus.isPauseAble(): Boolean {
    return this is KownTaskStatus.Queued || this is KownTaskStatus.Running || this is KownTaskStatus.PostProcessing
}

fun KownTaskStatus.asString(): String {
    return when (this) {
        is KownTaskStatus.Initial -> "Initial"
        is KownTaskStatus.Queued -> "Queued:${lastStatus.asString()}"
        is KownTaskStatus.Running -> "Running"
        is KownTaskStatus.PostProcessing -> "PostProcessing"
        is KownTaskStatus.Completed -> "Completed"
        is KownTaskStatus.Cancelled -> "Cancelled"
        is KownTaskStatus.Paused -> "Paused:${lastStatus.asString()}"
        is KownTaskStatus.Failed -> "Failed:$reason:${lastStatus.asString()}"
        is KownTaskStatus.Unknown -> "Unknown"
        else -> {
            throw Exception("Unreachable code")
        }
    }
}

fun stringAsTaskStatusMapper(str: String): KownTaskStatus {
    if (str.startsWith("Queued")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        return KownTaskStatus.Queued(lastStatus)
    }
    if (str.startsWith("Failed")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        val reason = str.split(":").dropLast(1).joinToString(":")
        return KownTaskStatus.Failed(reason, lastStatus)
    }
    if (str.startsWith("Paused")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        return KownTaskStatus.Paused(lastStatus)
    }
    return when (str) {
        "Initial" -> KownTaskStatus.Initial
        "Running" -> KownTaskStatus.Running
        "Completed" -> KownTaskStatus.Completed
        "PostProcessing" -> KownTaskStatus.PostProcessing
        "Cancelled" -> KownTaskStatus.Cancelled
        "Unknown" -> KownTaskStatus.Unknown
        else -> {
            throw Exception("Unreachable code")
        }
    }
}
