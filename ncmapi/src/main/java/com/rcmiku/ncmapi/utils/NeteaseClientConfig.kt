package com.rcmiku.ncmapi.utils

object NeteaseClientConfig {
    const val APP_VERSION = "9.4.32.251222163637"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mi A3 Build/QQ3A.200705.002; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/143.0.7499.34 Mobile Safari/537.36 NeteaseMusic/$APP_VERSION"

    val cookieOverrides = mapOf(
        CookieKeys.OS to "android",
        CookieKeys.APP_VER to APP_VERSION,
        CookieKeys.CHANNEL to "xiaomi",
        CookieKeys.VERSION_CODE to "6006066",
        CookieKeys.RESOLUTION to "2268x1080"
    )
}
