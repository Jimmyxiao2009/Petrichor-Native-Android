package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.FileProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object ReadResponseCache {
    private data class Entry(val saved:Long,val body:String)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val hot=ConcurrentHashMap<String,Entry>()
    private val flights=ConcurrentHashMap<String,Deferred<String>>()
    private val storage=Mutex()
    private val generation=java.util.concurrent.atomic.AtomicLong(0L)
    private val changes=MutableSharedFlow<String>(extraBufferCapacity=16)
    val updates=changes.asSharedFlow()
    private val readPaths=setOf("/album","/album/detail/dynamic","/album/new","/album/sublist","/artist/album","/artist/detail","/artist/top/song","/cloudsearch","/dj/detail","/dj/program","/likelist","/lyric","/personalized","/playlist/detail","/playlist/detail/dynamic","/recommend/songs","/search/suggest","/search/programs","/program/detail","/comment/list","/toplist","/user/cloud","/user/playlist","/user/record")
    private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun root()=FileProvider.cacheDir?.resolve("responses-v1")
    suspend fun request(path:String,params:Map<String,Any>,fetch:suspend()->String):String {
        if(path !in readPaths){
            val result=fetch()
            val code=runCatching{json.parseToJsonElement(result).jsonObject["code"]?.jsonPrimitive?.intOrNull}.getOrNull()
            if(code==200&&(path.startsWith("/playlist/")||path=="/song/like"||path.startsWith("/comment/")))clear()
            return result
        }
        val account=CookieProvider.getCookieMap()["MUSIC_U"].orEmpty()
        val key=hash(account+"|"+path+"|"+params.toSortedMap().toString());val epoch=generation.get();val flightKey="$epoch:$key"
        val value=hot[key] ?: withContext(Dispatchers.IO){storage.withLock {
            runCatching { val f=root()?.resolve("$key.json")?:return@withLock null;val data=json.parseToJsonElement(f.readText()).jsonObject;Entry(data.getValue("saved").jsonPrimitive.long,data.getValue("body").jsonPrimitive.content).also{hot[key]=it} }.getOrNull()
        }}
        fun refresh():Deferred<String> = synchronized(flights){
            flights[flightKey] ?: scope.async(start=CoroutineStart.LAZY){
                try {
                    val body=withTimeout(20_000){fetch()};val parsed=json.parseToJsonElement(body).jsonObject
                    val code=parsed["code"]?.jsonPrimitive?.intOrNull ?: 200
                    check(code==200){parsed["message"]?.jsonPrimitive?.contentOrNull?:"内容加载失败（$code）"}
                    if(epoch==generation.get()&&account==CookieProvider.getCookieMap()["MUSIC_U"].orEmpty()) {
                        val entry=Entry(System.currentTimeMillis(),body);val changed=hot[key]?.body!=body;hot[key]=entry
                        storage.withLock {runCatching{
                            val dir=root()?:return@runCatching;dir.mkdirs();val file=dir.resolve("$key.json");val temp=dir.resolve("$key.tmp")
                            temp.writeText(buildJsonObject{put("saved",entry.saved);put("body",body)}.toString());if(!temp.renameTo(file)){file.writeText(temp.readText());temp.delete()}
                            val files=dir.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}.orEmpty();var bytes=0L
                            files.forEachIndexed{i,f->bytes+=f.length();if(i>=128||bytes>24*1024*1024)f.delete()}
                            if(hot.size>128)hot.entries.sortedBy{it.value.saved}.take(hot.size-128).forEach{hot.remove(it.key)}
                        }}
                        if(changed)changes.tryEmit(path)
                    }
                    check(epoch==generation.get()&&account==CookieProvider.getCookieMap()["MUSIC_U"].orEmpty()) { "账号已变化，请重新打开页面" }
                    body
                }finally{synchronized(flights){flights.remove(flightKey)}}
            }.also{flights[flightKey]=it;it.start()}
        }
        val age=value?.let{System.currentTimeMillis()-it.saved} ?: Long.MAX_VALUE
        if(epoch!=generation.get()||account!=CookieProvider.getCookieMap()["MUSIC_U"].orEmpty()) throw CancellationException("Account changed")
        if(value!=null&&age in 0..7*24*60*60*1000L){if(age>(if(path=="/comment/list")60_000 else 300_000))refresh();return value.body}
        return refresh().await()
    }
    suspend fun clear(){
        generation.incrementAndGet();hot.clear();synchronized(flights){flights.clear()}
        withContext(Dispatchers.IO){storage.withLock{root()?.listFiles()?.forEach{it.delete()}}};changes.tryEmit("*")
    }
}
