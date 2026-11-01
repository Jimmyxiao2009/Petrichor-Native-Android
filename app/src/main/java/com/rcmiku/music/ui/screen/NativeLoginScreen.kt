package com.rcmiku.music.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.rcmiku.music.constants.*
import com.rcmiku.music.utils.dataStore
import com.rcmiku.music.ui.navigation.Screen
import com.rcmiku.ncmapi.api.NativeApi
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private object LoginCooldown { var availableAt=0L }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController:NavController) {
    var web by rememberSaveable{mutableStateOf(false)}
    if(web){WebLoginScreen(navController);return}
    var mode by rememberSaveable{mutableIntStateOf(0)}
    var account by rememberSaveable{mutableStateOf("")}
    var country by rememberSaveable{mutableStateOf("86")}
    var secret by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var visiblePassword by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var seconds by remember{mutableIntStateOf(0)}
    var qr by remember{mutableStateOf<Bitmap?>(null)}
    var qrRefresh by remember{mutableIntStateOf(0)}
    var qrMessage by remember{mutableStateOf("正在生成二维码…")}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    suspend fun finish(){
        val user=NativeApi.verifyLogin()
        context.dataStore.edit{it[ncmCookieKey]=json.encodeToString(CookieProvider.getCookieMap());it[userIdKye]=user.account.profile.userId}
        navController.navigate(Screen.Library.route){popUpTo(Screen.Home.route);launchSingleTop=true}
    }
    LaunchedEffect(Unit){while(true){seconds=((LoginCooldown.availableAt-System.currentTimeMillis()+999)/1000).toInt().coerceAtLeast(0);delay(500)}}
    LaunchedEffect(mode,qrRefresh){
        qr=null;if(mode!=2)return@LaunchedEffect
        try{
            qrMessage="正在生成二维码…"
            val response=withContext(Dispatchers.IO){NativeApi.call("/login/qr/key")}
            val key=(response["unikey"]?:response["data"]?.jsonObject?.get("unikey"))?.jsonPrimitive?.content ?: error("未返回二维码标识")
            qr=withContext(Dispatchers.Default){
                val matrix=MultiFormatWriter().encode("https://music.163.com/login?codekey=$key",BarcodeFormat.QR_CODE,600,600)
                Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888).apply{val pixels=IntArray(600*600){i->if(matrix[i%600,i/600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE};setPixels(pixels,0,600,0,0,600,600)}
            }
            qrMessage="使用网易云音乐扫描，或切换手机验证码登录"
            repeat(72){
                delay(2500)
                val state=withContext(Dispatchers.IO){NativeApi.call("/login/qr/check",mapOf("key" to key),setOf(800,801,802,803))}["code"]?.jsonPrimitive?.intOrNull
                when(state){800->error("二维码已过期，请刷新");802->qrMessage="已扫描，请在手机确认";803->{finish();return@LaunchedEffect}}
            };qrMessage="二维码已过期，请刷新"
        }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(e:Exception){qrMessage=e.message?:"二维码加载失败"}
    }
    Scaffold(topBar={TopAppBar(title={Text("登录网易云音乐")},navigationIcon={IconButton(onClick={navController.navigateUp()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            TabRow(selectedTabIndex=mode){listOf("验证码","密码","扫码").forEachIndexed{i,label->Tab(selected=mode==i,onClick={mode=i;secret="";error=""},text={Text(label)})}}
            if(mode<2){
                if(mode==0||'@' !in account)OutlinedTextField(value=country,onValueChange={country=it.filter(Char::isDigit).take(4)},label={Text("国家区号")},prefix={Text("+")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.width(150.dp))
                OutlinedTextField(value=account,onValueChange={account=it},singleLine=true,label={Text(if(mode==0) "手机号码" else "手机号码或邮箱")},keyboardOptions=KeyboardOptions(keyboardType=if(mode==0) KeyboardType.Phone else KeyboardType.Email),modifier=Modifier.fillMaxWidth(),enabled=!busy)
                OutlinedTextField(value=secret,onValueChange={secret=it},label={Text(if(mode==0) "短信验证码" else "密码")},singleLine=true,visualTransformation=if(mode==1&&!visiblePassword) PasswordVisualTransformation() else VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(mode==0) KeyboardType.NumberPassword else KeyboardType.Password),trailingIcon={if(mode==1)TextButton(onClick={visiblePassword=!visiblePassword}){Text(if(visiblePassword) "隐藏" else "显示")}},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                if(mode==0)OutlinedButton(enabled=!busy&&seconds==0,onClick={scope.launch{busy=true;error="";try{withContext(Dispatchers.IO){NativeApi.sendCode(account,country)};LoginCooldown.availableAt=System.currentTimeMillis()+60_000;seconds=60}catch(e:Exception){error=e.message?:"发送失败"}finally{busy=false}}}){Text(if(seconds>0) "${seconds} 秒后重新获取" else "获取验证码")}
                if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
                Button(enabled=!busy&&account.isNotBlank()&&secret.isNotBlank(),onClick={scope.launch{busy=true;error="";try{withContext(Dispatchers.IO){NativeApi.login(account,secret,mode==1,country)};secret="";finish()}catch(e:Exception){error=e.message?:"登录失败，请重试"}finally{busy=false}}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Text("登录")}
            }else{
                qr?.let{Image(it.asImageBitmap(),"登录二维码",Modifier.fillMaxWidth().padding(24.dp).aspectRatio(1f))}
                Text(qrMessage,style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={qrRefresh++}){Text("刷新二维码")}
            }
            TextButton(onClick={web=true}){Text("网页登录 / 完成安全验证")}
            Spacer(Modifier.height(24.dp))
        }
    }
}
