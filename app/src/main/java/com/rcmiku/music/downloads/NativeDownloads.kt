package com.rcmiku.music.downloads

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import com.rcmiku.music.R
import com.rcmiku.music.constants.audioQualityKey
import com.rcmiku.music.utils.dataStore
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object NativeDownloads {
    const val TAG="petrichor-download"
    fun folder(context:Context)=context.getSharedPreferences(TAG,Context.MODE_PRIVATE).getString("folder",null)
    fun setFolder(context:Context,uri:Uri){context.getSharedPreferences(TAG,Context.MODE_PRIVATE).edit().putString("folder",uri.toString()).apply()}
    fun enqueue(context:Context,song:Song){
        val request=OneTimeWorkRequestBuilder<AudioDownloadWorker>().setInputData(workDataOf("songId" to song.id,"name" to song.name,"artist" to song.ar.joinToString(" / "){it.name},"folder" to folder(context).orEmpty())).addTag(TAG).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("download-${song.id}",ExistingWorkPolicy.KEEP,request)
    }
    fun safeName(value:String)=value.map{if(it in "\\/:*?\"<>|" || it.isISOControl()) '_' else it}.joinToString("").trim().take(100).ifBlank{"audio"}
}

class AudioDownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        var destination:Uri?=null;var connection:HttpURLConnection?=null
        try{
            val songId=inputData.getLong("songId",0);check(songId>0){"歌曲无效"}
            val name=inputData.getString("name").orEmpty()
            val manager=applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("downloads","音乐下载",NotificationManager.IMPORTANCE_LOW))
            val notification=NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(name).setContentText("正在下载").setOngoing(true).setProgress(0,0,true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"取消",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
            setForeground(ForegroundInfo(id.hashCode(),notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            val pref=applicationContext.dataStore.data.first()[audioQualityKey]
            val level=SongLevel.entries.firstOrNull{it.name==pref}?:SongLevel.LOSSLESS
            val stream=PlayerApi.songPlayUrlV1(songId.toString(),level).getOrThrow().data.firstOrNull{!it.url.isNullOrBlank()}?:error("暂无可下载音源")
            val url=URL(stream.url);check(url.protocol in setOf("https","http")){"下载地址无效"}
            connection=(url.openConnection() as HttpURLConnection).apply{connectTimeout=20_000;readTimeout=30_000;instanceFollowRedirects=true;connect()}
            check(connection.responseCode in 200..299){"下载失败（HTTP ${connection.responseCode}）"}
            val source=java.io.BufferedInputStream(connection.inputStream)
            source.mark(32);val header=ByteArray(16);val count=source.read(header);source.reset()
            val isFlac=count>=4&&header[0]==102.toByte()&&header[1]==76.toByte()&&header[2]==97.toByte()&&header[3]==67.toByte()
            val type=when{isFlac->"flac";stream.type?.lowercase() in setOf("flac","mp3","m4a","aac","ogg","wav")->stream.type!!.lowercase();connection.contentType?.contains("mp4")==true->"m4a";else->"mp3"}
            check(type!="flac"||isFlac){"返回的音频并非 FLAC，请更换音质后重试"}
            check(connection.contentType?.contains("text/")!=true){"音源返回了错误页面"}
            val fileName=NativeDownloads.safeName("$name - ${inputData.getString("artist").orEmpty()}")+".$type"
            val mime=when(type){"flac"->"audio/flac";"m4a"->"audio/mp4";"ogg"->"audio/ogg";"wav"->"audio/wav";else->"audio/mpeg"}
            val folder=inputData.getString("folder").orEmpty()
            destination=if(folder.isNotBlank()){
                val tree=Uri.parse(folder);val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(applicationContext.contentResolver,parent,mime,fileName)?:error("无法在所选文件夹创建文件")
            }else{
                val root=File(applicationContext.filesDir,"downloads").apply{mkdirs()};val file=File(root,"$songId-$fileName")
                FileProvider.getUriForFile(applicationContext,applicationContext.packageName+".downloads",file)
            }
            val total=connection.contentLengthLong;var copied=0L;var last=0L
            source.use{input->applicationContext.contentResolver.openOutputStream(destination,"w")?.use{output->
                val buffer=ByteArray(64*1024)
                while(true){ensureActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);copied+=n
                    if(System.currentTimeMillis()-last>500){setProgress(workDataOf("percent" to if(total>0)(copied*100/total).toInt() else -1,"name" to name,"bytes" to copied));last=System.currentTimeMillis()}
                }
            }?:error("无法写入下载目录")}
            check(copied>0&&(total<=0||copied==total)){"下载未完成，请重试"}
            Result.success(workDataOf("uri" to destination.toString(),"name" to name,"format" to type.uppercase(),"bytes" to copied,"mime" to mime))
        }catch(cancel:CancellationException){destination?.let{runCatching{applicationContext.contentResolver.delete(it,null,null)}};throw cancel}
        catch(error:Exception){destination?.let{runCatching{applicationContext.contentResolver.delete(it,null,null)}};Result.failure(workDataOf("error" to (error.message?:"下载失败")))}
        finally{connection?.disconnect()}
    }
}
