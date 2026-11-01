package com.rcmiku.music.ui.screen

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.rcmiku.music.R
import com.rcmiku.music.constants.ncmCookieKey
import com.rcmiku.music.ui.navigation.Screen
import com.rcmiku.music.utils.getDeviceID
import com.rcmiku.music.utils.rememberPreference
import com.rcmiku.ncmapi.utils.CookieKeys
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString


@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(
    navController: NavController,
) {

    var ncmCookie by rememberPreference(ncmCookieKey, "")
    var webView: WebView? = null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (webView?.canGoBack() == true)
                                webView?.goBack()
                            else
                                navController.navigateUp()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        this.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                if (url?.startsWith("https://y.music.163.com/m") == true) {
                                    val cookieManager = CookieManager.getInstance()
                                    val cookieMap = cookieManager.collectLoginCookies(url)
                                    if (!cookieMap.containsKey(CookieKeys.MUSIC_U)) {
                                        Toast.makeText(
                                            context,
                                            "登录状态尚未生效，请完成登录后稍等片刻",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return
                                    }
                                    cookieMap.addDeviceInfo()
                                    ncmCookie = json.encodeToString(cookieMap)
                                    CookieProvider.init(cookieMap)
                                    view?.clearCache(true)
                                    navController.navigate(Screen.Home.route)
                                }
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode
                        }
                        webView = this
                        loadUrl("https://music.163.com/m/login")
                    }
                }
            )
        }
    }
}

private fun CookieManager.collectLoginCookies(currentUrl: String): MutableMap<String, String> {
    flush()
    val cookieUrls = linkedSetOf(
        "https://music.163.com",
        "https://y.music.163.com",
        currentUrl
    )
    return buildMap {
        cookieUrls.forEach { url ->
            getCookie(url)?.let { putAll(parseCookieString(it)) }
        }
    }.toMutableMap()
}

private fun MutableMap<String, String>.addDeviceInfo() {
    this[CookieKeys.DEVICE_ID] = getDeviceID()
    this[CookieKeys.OS_VER] = Build.VERSION.RELEASE
    this[CookieKeys.MOBILE_NAME] = Build.MODEL
}
