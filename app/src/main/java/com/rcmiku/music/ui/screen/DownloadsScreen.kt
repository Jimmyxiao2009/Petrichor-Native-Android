package com.rcmiku.music.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.work.*
import com.rcmiku.music.downloads.NativeDownloads
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(nav:NavHostController){
    val player=com.rcmiku.music.LocalPlayerController.current.controller
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val work=remember{WorkManager.getInstance(context)}
    val jobs by remember{work.getWorkInfosByTagFlow(NativeDownloads.TAG)}.collectAsState(emptyList())
    var folder by remember{mutableStateOf(NativeDownloads.folder(context))}
    var error by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null)try{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);NativeDownloads.setFolder(context,uri);folder=uri.toString();error=null}catch(e:Exception){error="无法取得文件夹权限，请重新选择"}
    }
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)try{
            context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var name="本地音频"
            context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0)}
            player?.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(uri).setMediaId((-kotlin.math.abs(uri.toString().hashCode().toLong())).toString()).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(name).setArtist("本地音频").build()).build())
            player?.prepare();player?.play()
        }catch(e:Exception){error=e.message?:"无法打开音频"}
    }
    Scaffold(topBar={TopAppBar(title={Text("下载")},navigationIcon={IconButton({nav.navigateUp()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("保存位置",style=MaterialTheme.typography.titleMedium)
                    Text(folder?.let{android.net.Uri.decode(it).substringAfterLast("/tree/") }?:"应用文件夹",style=MaterialTheme.typography.bodySmall)
                    TextButton({picker.launch(folder?.toUri())}){Text("选择文件夹")}
                    if(jobs.any{!it.state.isFinished})Text("新目录对下一次下载生效",style=MaterialTheme.typography.labelSmall)
                }
            }}
            item{TextButton({audioPicker.launch(arrayOf("audio/*"))}){Text("打开本地音频（含 FLAC）")}}
            error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
            if(jobs.isEmpty())item{Text("在歌曲菜单中选择“下载”，任务会显示在这里。",style=MaterialTheme.typography.bodyMedium)}
            items(jobs.sortedByDescending{it.id.toString()},key={it.id}){job->
                val data=job.outputData
                ElevatedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(data.getString("name")?:job.progress.getString("name")?:"音乐下载",style=MaterialTheme.typography.titleSmall)
                        val percent=job.progress.getInt("percent",0)
                        Text(when(job.state){WorkInfo.State.SUCCEEDED->"${data.getString("format")} · ${"%.1f".format(data.getLong("bytes",0)/1048576.0)} MB";WorkInfo.State.FAILED->data.getString("error")?:"下载失败，请回到歌曲菜单重试";WorkInfo.State.CANCELLED->"已取消";WorkInfo.State.RUNNING->if(percent>=0)"$percent%" else "正在下载…";else->"等待网络或开始下载…"},style=MaterialTheme.typography.bodySmall,color=if(job.state==WorkInfo.State.FAILED)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(job.state==WorkInfo.State.RUNNING)LinearProgressIndicator(progress={percent.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())
                        if(!job.state.isFinished)TextButton({work.cancelWorkById(job.id)}){Text("取消")}
                        if(job.state==WorkInfo.State.SUCCEEDED)TextButton({try{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(data.getString("uri")?.toUri(),data.getString("mime")?:"audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(e:Exception){error="找不到可打开此音频的应用"}}){Text("打开音频")}
                    }
                }
            }
        }
    }
}
