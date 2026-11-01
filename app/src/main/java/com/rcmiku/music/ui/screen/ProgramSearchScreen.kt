package com.rcmiku.music.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.rcmiku.music.ui.navigation.ProgramSearchNav
import com.rcmiku.music.ui.navigation.RadioNav
import com.rcmiku.ncmapi.api.NativeApi
import com.rcmiku.ncmapi.api.ProgramMatch
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSearchScreen(nav:NavHostController){
    val initial=remember{nav.currentBackStackEntry?.toRoute<ProgramSearchNav>()?.query.orEmpty()}
    var query by rememberSaveable{mutableStateOf(initial)};var results by remember{mutableStateOf<List<ProgramMatch>>(emptyList())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var more by remember{mutableStateOf(false)}
    var offset by remember{mutableIntStateOf(0)};var searched by remember{mutableStateOf(false)};var opening by remember{mutableStateOf<Long?>(null)}
    var job by remember{mutableStateOf<Job?>(null)};val scope=rememberCoroutineScope();val keyboard=LocalSoftwareKeyboardController.current
    fun search(reset:Boolean){job?.cancel();keyboard?.hide();busy=true;error=null;searched=true
        job=scope.launch{try{val start=if(reset)0 else offset;val list=NativeApi.programSearch(query,start);results=(if(reset)list else results+list).distinctBy{it.id};offset=start+30;more=list.size>=30}catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=e.message}finally{busy=false}}}
    LaunchedEffect(Unit){if(initial.isNotBlank())search(true)}
    Scaffold(topBar={TopAppBar(title={Text("搜节目找播客")},navigationIcon={IconButton({nav.navigateUp()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            OutlinedTextField(query,{if(it.length<=100)query=it},placeholder={Text("输入节目名或节目中的歌曲")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(query.isNotBlank())search(true)}),trailingIcon={TextButton({search(true)},enabled=query.isNotBlank()){Text("搜索")}})
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=12.dp)){
                error?.let{item{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error);TextButton({search(results.isEmpty())}){Text("重试")}}}
                if(searched&&!busy&&results.isEmpty()&&error==null)item{Text("没有找到节目，试试其他歌名或节目关键词",Modifier.padding(16.dp))}
                items(results,key={it.id}){program->ListItem(headlineContent={Text(program.name)},supportingContent={Text(if(opening==program.id)"正在打开播客…" else program.radioName.ifBlank{"查看所属播客"})},modifier=Modifier.clickable(enabled=opening==null){
                    opening=program.id;scope.launch{try{val radioId=if(program.radioId>0)program.radioId else NativeApi.call("/program/detail",mapOf("id" to program.id))["program"]?.jsonObject?.get("radio")?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull?:0
                        check(radioId>0){"此节目未返回所属播客"};nav.navigate(RadioNav(radioId))
                    }catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=e.message}finally{opening=null}}
                })}
                if(more)item{TextButton({search(false)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("加载更多")}}
            }
        }
    }
}
