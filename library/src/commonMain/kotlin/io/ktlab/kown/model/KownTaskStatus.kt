package io.ktlab.kown.model

interface TaskStatus {
    data object Initial : TaskStatus
    data class Queued(val lastStatus: TaskStatus) : TaskStatus
    data object Running : TaskStatus
    data object PostProcessing : TaskStatus

    data class Paused(val lastStatus: TaskStatus) : TaskStatus
    data object Completed : TaskStatus

    data object Cancelled : TaskStatus

    data class Failed(val reason:String, val lastStatus: TaskStatus) : TaskStatus
    data object Unknown : TaskStatus
}


fun TaskStatus.isProcessing():Boolean {
    return this == TaskStatus.Running || this == TaskStatus.PostProcessing
}
fun TaskStatus.isWaiting():Boolean {
    return this is TaskStatus.Queued
}
fun TaskStatus.isSuccessFinite():Boolean {
    return this == TaskStatus.Completed || this == TaskStatus.Cancelled
}
fun TaskStatus.isFinite():Boolean {
    return this == TaskStatus.Completed || this == TaskStatus.Cancelled
            || this is TaskStatus.Failed || this is TaskStatus.Paused
}

internal fun TaskStatus.isPauseAble():Boolean {
    return this is TaskStatus.Queued || this is TaskStatus.Running || this is TaskStatus.PostProcessing
}



fun TaskStatus.asString():String {
    return when(this) {
        is TaskStatus.Initial -> "Initial"
        is TaskStatus.Queued -> "Queued:${lastStatus.asString()}"
        is TaskStatus.Running -> "Running"
        is TaskStatus.PostProcessing -> "PostProcessing"
        is TaskStatus.Completed -> "Completed"
        is TaskStatus.Cancelled -> "Cancelled"
        is TaskStatus.Paused -> "Paused:${lastStatus.asString()}"
        is TaskStatus.Failed -> "Failed:$reason:${lastStatus.asString()}"
        is TaskStatus.Unknown -> "Unknown"
        else -> { throw Exception("Unreachable code")}
    }
}

fun stringAsTaskStatusMapper(str: String): TaskStatus {
    if (str.startsWith("Queued")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        return TaskStatus.Queued(lastStatus)
    }
    if (str.startsWith("Failed")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        val reason = str.split(":").dropLast(1).joinToString(":")
        return TaskStatus.Failed(reason, lastStatus)
    }
    if (str.startsWith("Paused")) {
        val lastStatus = stringAsTaskStatusMapper(str.split(":").last())
        return TaskStatus.Paused(lastStatus)
    }
    return when (str) {
        "Initial" -> TaskStatus.Initial
        "Running" -> TaskStatus.Running
        "Completed" -> TaskStatus.Completed
        "PostProcessing" -> TaskStatus.PostProcessing
        "Cancelled" -> TaskStatus.Cancelled
        "Unknown" -> TaskStatus.Unknown
        else -> { throw Exception("Unreachable code")}
    }
}