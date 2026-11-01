package com.rcmiku.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rcmiku.ncmapi.api.NativeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CreatePlaylistDialog(onDismiss:()->Unit,onCreated:(Long)->Unit) {
    var name by remember { mutableStateOf("") }
    var privatePlaylist by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("新建歌单")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{if(it.length<=40)name=it},label={Text("歌单名称")},singleLine=true,enabled=!busy)
            Row { Checkbox(privatePlaylist,{privatePlaylist=it},enabled=!busy);TextButton({privatePlaylist=!privatePlaylist},enabled=!busy){Text("私密歌单")} }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={
        busy=true;error=null;scope.launch {
            try{val id=NativeApi.createPlaylist(name,privatePlaylist);onCreated(id)}
            catch(cancel:CancellationException){throw cancel}
            catch(e:Exception){error=e.message?:"创建失败"}
            finally{busy=false}
        }
    }){Text(if(busy)"正在创建…" else "创建")}},dismissButton={TextButton(onDismiss,enabled=!busy){Text("取消")}})
}

@Composable
fun NativeMenuAction(label:String,onClick:()->Unit,enabled:Boolean=true) {
    Surface(onClick=onClick,enabled=enabled,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
        Text(label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(horizontal=16.dp,vertical=16.dp))
    }
}
