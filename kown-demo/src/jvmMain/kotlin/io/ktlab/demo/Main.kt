package io.ktlab.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.ktlab.kown.KownDownloader
import kotlin.system.exitProcess
import io.ktlab.kown.model.DownloadListener
import io.ktlab.kown.model.DownloadTaskBO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random


private val example = listOf(
    "http://ipv4.download.thinkbroadband.com/1GB.zip" to "1GB.zip",
    "http://ipv4.download.thinkbroadband.com/200MB.zip" to "200MB.zip",
    "http://ipv4.download.thinkbroadband.com/50MB.zip" to "50MB.zip",
//    "https://r2cdn.beatsaver.com/4c297d04416ed753a0729b183c9d41b3a5bd5d7b.zip",
//    "https://r2cdn.beatsaver.com/1ac85b7042998acda9f357cbf415a36254c3c027.zip",
//    "https://r2cdn.beatsaver.com/ef37054a9d90110fa77fe1175a4578f285a66488.zip",
//    "https://r2cdn.beatsaver.com/51d7329919fcb213402198bef03ba4e97ceadd16.zip",
//    "https://r2cdn.beatsaver.com/fcc6464143021f357ff40db48cbb8425cc906a5d.zip",
//    "https://r2cdn.beatsaver.com/cdea34e5b42f29c193aec396eec7aa804954962e.zip",
//    "https://r2cdn.beatsaver.com/a176a93c276a1d4159e6dcf0fcb82baa754ac8e9.zip",
//    "https://r2cdn.beatsaver.com/ee028767512751d4d936198a704efada42717257.zip",
//    "https://r2cdn.beatsaver.com/5b91862885b2091a398e302b2a882a1a6c1d1c28.zip",
//    "https://r2cdn.beatsaver.com/dd7d1d82d9b47054a2637dece7182a47b47ef917.zip"
)
fun main() {
    val kownloader = KownDownloader.new().build()
    val requests = mutableListOf<DownloadTaskBO>()
    val scope = CoroutineScope(Dispatchers.Main)
    val ioScope = CoroutineScope(Dispatchers.IO)
//    val downloadTaskFlow = kownloader.getAllDownloadTaskFlow().flowOn(Dispatchers.IO)

    val list = MutableStateFlow(listOf<DownloadTaskBO>())
    ioScope.launch {
        kownloader.getAllDownloadTaskFlow().collect {res->
            val tmp = res
                .map { it.copyTask() }
            list.update { tmp }
        }
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "BSHelper",
            state = WindowState(size = DpSize(1440.dp, 768.dp))
        ) {
            Column{
                RequestForm(
                    onSubmit = {url,fn->
                        buildRequestTask(kownloader,url,fn).let {kownloader.enqueue(it) }
                    }
                )
                TextButton(onClick = {
                    val index = Random.nextInt(0, example.size)
                    val url = example[index].first
                    val fn = example[index].second
                    buildRequestTask(kownloader,url,fn).let {kownloader.enqueue(it) }
                }) {
                    Text("Add example task")
                }
                val tasks = list.collectAsState()
                Column {
                    for (downloadTaskBO in tasks.value) {
                        DownloadTaskListItem(downloadTaskBO,kownloader)
                    }
                }
            }
        }
    }
    exitProcess(0)
}


@Composable
fun RequestForm(onSubmit:(String,String)->Unit) {
    Column {
        val url = remember { mutableStateOf("") }
        val filename = remember { mutableStateOf("") }
        TextField(
            value = url.value,
            onValueChange = {
                url.value = it
            },
            label = {
                Text("URL")
            }
        )
        TextField(
            value = filename.value,
            onValueChange = {
                filename.value = it
            },
            label = {
                Text("filename")
            }
        )
        Row {
            TextButton(onClick = {
                url.value = ""
                filename.value = ""
            }) {
                Text("Clear")
            }
            TextButton(onClick = {
                onSubmit(url.value,filename.value)
            }) {
                Text("Submit")
            }
        }
    }


}

fun buildRequestTask(kown: KownDownloader,url:String,filename:String):DownloadTaskBO {
    return kown.newRequestBuilder(
        url, "kown-download", filename)
        .setTag("google")
        .build()
}
fun startDownload(kown: KownDownloader,requestList:List<DownloadTaskBO>){
    for (request in requestList) {
        val listener = DownloadListener(
//
//            onStart = { progress ->
//                println("onProgress: $progress")
//            },
//            onCompleted = { task ->
//                println("onCompleted: ${task.taskId}")
//            },
//            onFailed = { task ->
//                println("onFailed: ${task.taskId}")
//            },
//            onPaused = { task ->
//                println("onPaused: ${task.taskId}")
//            },
//            onProgress = { progress ->
//                println("onProgress: $progress")
//            },
//            onResumed = { task ->
//                println("onResumed: ${task.taskId}")
//            },
//            onError = { task, exception ->
//                println("onError: ${task.taskId}, ${exception.message}")
//            },
//            onCancelled = { task ->
//                println("onCancelled: ${task.taskId}")
//            }
        )
        kown.enqueue(request, listener)
    }
}