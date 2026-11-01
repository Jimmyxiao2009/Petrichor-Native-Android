package com.rcmiku.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rcmiku.music.constants.userIdKye
import com.rcmiku.music.utils.rememberPreference
import com.rcmiku.ncmapi.api.NativeApi
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.account.UserPlaylistType
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.model.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListBottomSheet(song:Song?,openBottomSheet:Boolean,onDismiss:()->Unit) {
    if(!openBottomSheet||song==null)return
    val userId by rememberPreference(userIdKye,0L)
    var playlists by remember{mutableStateOf<List<Playlist>?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var create by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun add(id:Long){
        if(busy)return
        busy=true;error=null
        scope.launch {try{NativeApi.manipulate(id,listOf(song.id),false);onDismiss()}
        catch(cancel:CancellationException){throw cancel}
        catch(e:Exception){error=e.message?:"添加失败"}finally{busy=false}}
    }
    LaunchedEffect(userId){if(userId>0)AccountApi.userPlaylist(userId,UserPlaylistType.CREATE).onSuccess{playlists=it.data?.playlist.orEmpty().filter{p->p.creator?.userId==userId}}.onFailure{error=it.message}}
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            item{Text("添加到歌单",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=12.dp))}
            if(userId<=0)item{Text("请先在“我的”页面登录")}
            else {
                item{NativeMenuAction("新建歌单",{create=true},!busy)}
                if(playlists==null&&error==null)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                items(playlists.orEmpty(),key={it.id}){p->NativeMenuAction(p.name,{add(p.id)},!busy)}
                if(playlists?.isEmpty()==true)item{Text("还没有歌单，可以先新建一个")}
            }
            error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        }
    }
    if(create)CreatePlaylistDialog({create=false}){id->create=false;add(id)}
}
