package com.rcmiku.music.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.rcmiku.music.ui.navigation.CommentsNav
import com.rcmiku.music.ui.navigation.Screen
import com.rcmiku.ncmapi.api.NativeApi
import com.rcmiku.ncmapi.api.ReadResponseCache
import com.rcmiku.ncmapi.utils.CookieProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private fun JsonObject.text(key:String)=get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.number(key:String)=get(key)?.jsonPrimitive?.longOrNull?:0L

data class CommentState(val items:List<JsonObject> = emptyList(),val total:Long=0,val busy:Boolean=false,val error:String?=null,val more:Boolean=false,val sort:Int=99)
@HiltViewModel
class CommentsViewModel @Inject constructor(handle:SavedStateHandle):ViewModel(){
    val route=handle.toRoute<CommentsNav>()
    val thread=if(route.programId>0)"A_DJ_1_"+route.programId else "R_SO_4_"+route.songId
    private val mutable=MutableStateFlow(CommentState())
    val state=mutable.asStateFlow()
    private var page=1;private var cursor="0";private var job:Job?=null
    init{load(true);viewModelScope.launch{ReadResponseCache.updates.collect{if(it=="/comment/list"&&!mutable.value.busy)load(true)}}}
    fun sort(value:Int){if(mutable.value.sort==value)return;job?.cancel();mutable.value=CommentState(sort=value);page=1;cursor="0";load(true)}
    fun load(reset:Boolean=false){
        if(mutable.value.busy)return
        mutable.update{it.copy(busy=true,error=null)}
        job=viewModelScope.launch {
            try{
                val next=if(reset)1 else page
                val data=NativeApi.comments(thread,next,mutable.value.sort,if(reset)"0" else cursor)["data"]?.jsonObject?:error("评论暂时不可用")
                val list=(data["comments"] as? JsonArray).orEmpty().mapNotNull{it as? JsonObject}
                page=next+1;cursor=data.text("cursor")
                mutable.update{it.copy(items=(if(reset)list else it.items+list).distinctBy{it.number("commentId")},total=data.number("totalCount"),more=data["hasMore"]?.jsonPrimitive?.booleanOrNull==true&&list.isNotEmpty())}
            }catch(cancel:CancellationException){throw cancel}catch(e:Exception){mutable.update{it.copy(error=e.message?:"加载失败")}}finally{mutable.update{it.copy(busy=false)}}
        }
    }
    suspend fun post(text:String,reply:Long){NativeApi.postComment(thread,text,reply);load(true)}
    fun like(comment:JsonObject){viewModelScope.launch {
        try{check(CookieProvider.isLoggedIn()){"请先登录"};val liked=comment["liked"]?.jsonPrimitive?.booleanOrNull==true
            NativeApi.call("/comment/like",mapOf("threadId" to thread,"commentId" to comment.number("commentId"),"liked" to !liked))
            mutable.update{s->s.copy(items=s.items.map{if(it.number("commentId")==comment.number("commentId"))JsonObject(it+mapOf("liked" to JsonPrimitive(!liked),"likedCount" to JsonPrimitive((it.number("likedCount")+if(liked)-1 else 1).coerceAtLeast(0)))) else it})}
        }catch(cancel:CancellationException){throw cancel}catch(e:Exception){mutable.update{it.copy(error=e.message)}}
    }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(nav:NavHostController,vm:CommentsViewModel=hiltViewModel()){
    val state by vm.state.collectAsState()
    var draft by remember{mutableStateOf("")};var reply by remember{mutableStateOf<JsonObject?>(null)}
    var sending by remember{mutableStateOf(false)};var sendError by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Scaffold(topBar={TopAppBar(title={Column{Text("评论",style=MaterialTheme.typography.titleLarge);Text(vm.route.title,maxLines=1,style=MaterialTheme.typography.labelMedium)}},navigationIcon={IconButton({nav.navigateUp()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})},bottomBar={
        Surface(tonalElevation=2.dp){Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=6.dp)){
            reply?.let{Row(verticalAlignment=Alignment.CenterVertically){Text("回复 "+((it["user"] as? JsonObject)?.text("nickname")?:""),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton({reply=null}){Text("取消回复")}}}
            sendError?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(draft,{if(it.length<=1000)draft=it},placeholder={Text("写评论…")},minLines=1,maxLines=3,modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.extraLarge,enabled=!sending)
                TextButton(enabled=!sending&&draft.isNotBlank(),onClick={
                    if(!CookieProvider.isLoggedIn()){nav.navigate(Screen.Login.route)}else{sending=true;sendError=null;scope.launch{try{vm.post(draft,reply?.number("commentId")?:0);draft="";reply=null}catch(cancel:CancellationException){throw cancel}catch(e:Exception){sendError=e.message}finally{sending=false}}}
                }){Text(if(sending)"发送中" else "发送")}
            }
        }}
    }){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(99 to "推荐",2 to "最热",3 to "最新").forEach{(value,name)->FilterChip(selected=state.sort==value,onClick={vm.sort(value)},label={Text(name)})};Spacer(Modifier.weight(1f));Text(state.total.toString(),style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=18.dp))}}
            if(state.busy&&state.items.isEmpty())items(4){ListItem(headlineContent={Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth(.65f).height(16.dp)){}},supportingContent={Surface(color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.padding(top=8.dp).fillMaxWidth().height(32.dp)){} })}
            items(state.items,key={it.number("commentId")}){comment->
                val user=comment["user"] as? JsonObject
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    AsyncImage(user?.text("avatarUrl"),null,modifier=Modifier.size(32.dp).clip(CircleShape))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(user?.text("nickname")?:"云音乐用户",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(comment.text("content"),style=MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Text(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(comment.number("time"))),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                            TextButton({reply=comment},contentPadding=PaddingValues(horizontal=8.dp)){Text("回复")}
                            TextButton({vm.like(comment)},contentPadding=PaddingValues(horizontal=8.dp)){Text((if(comment["liked"]?.jsonPrimitive?.booleanOrNull==true)"已赞 " else "赞 ")+comment.number("likedCount"))}
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
                    }
                }
            }
            state.error?.let{item{Text(it,color=MaterialTheme.colorScheme.error);TextButton({vm.load(state.items.isEmpty())}){Text("重试")}}}
            if(!state.busy&&state.items.isEmpty()&&state.error==null)item{Text("还没有评论")}
            if(state.more)item{TextButton({vm.load()},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(if(state.busy)"正在加载…" else "加载更多")}}
        }
    }
}
