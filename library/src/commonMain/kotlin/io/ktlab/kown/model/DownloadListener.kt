package io.ktlab.kown.model


data class DownloadListener(
    var onStart: (DownloadTaskBO) -> Unit = {},
    var onProgress: (progress:Float) -> Unit = {},
    var onCompleted: (DownloadTaskBO) -> Unit = {},
    var onPaused: (DownloadTaskBO) -> Unit = {},
    var onResumed: (DownloadTaskBO) -> Unit = {},
    var onFailed: (DownloadTaskBO) -> Unit = {},
    var onCancelled: (DownloadTaskBO) -> Unit = {},
    var onError: (DownloadTaskBO, Exception) -> Unit = { _, _ ->}
) {
    companion object {
        val DEFAULT = DownloadListener()
    }
}