package io.ktlab.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.ktlab.kown.KownDownloader
import io.ktlab.kown.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

//@Composable
//fun DownloadTaskList(
//    downloadTaskBOs: List<DownloadTaskBO>
//) {
//    Column {
//        for (downloadTaskBO in downloadTaskBOs) {
//            DownloadTaskListItem(downloadTaskBO)
//        }
//    }
//}
@Composable
fun DownloadTaskListItem(
    downloadTaskBO: DownloadTaskBO,
    kown:KownDownloader
) {
    Card {
        Column {
            val id = downloadTaskBO.taskId
            var progress = 0f
            if (downloadTaskBO.totalBytes > 0) {
                progress = ((downloadTaskBO.downloadedBytes * 100f) / downloadTaskBO.totalBytes)/100.0f
            }
            Text("taskId:$id")


            Text("filename:${downloadTaskBO.filename}")

            Text("status:${downloadTaskBO.status.asString()}")
            val progressAnimate by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
            )
            Column (modifier = Modifier.widthIn(200.dp)){
                if(downloadTaskBO.status.isProcessing()){
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("progress:${progress}%")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("${byteToMB(downloadTaskBO.speed)}MB/s")
                    }
                }
                LinearProgressIndicator(progress = progressAnimate, strokeCap = StrokeCap.Round)
                if (downloadTaskBO.status.isProcessing()){
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("total:${byteToMB(downloadTaskBO.downloadedBytes)}/${byteToMB(downloadTaskBO.totalBytes)}MB")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("estimateTime:${downloadTaskBO.estimatedTime} s")
                    }
                }
            }
            if (downloadTaskBO.status.isProcessing()) {
                TextButton(onClick = {
                        kown.pauseById(downloadTaskBO.taskId)

                }) {
                    Text("pause")
                }
            }
            if (downloadTaskBO.status is TaskStatus.Paused) {
                TextButton(onClick = {
                    kown.resumeById(downloadTaskBO.taskId)
                }) {
                    Text("resume")
                }
            }

        }
    }
}

fun byteToMB(bytes: Long): String {
    return String.format("%.2f", bytes / 1024.0 / 1024.0)
}