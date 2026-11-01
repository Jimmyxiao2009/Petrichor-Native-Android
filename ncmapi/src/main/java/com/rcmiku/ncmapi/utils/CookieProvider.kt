package com.rcmiku.ncmapi.utils

object CookieProvider {
    private var cookieMap: Map<String, String> = emptyMap()
    var cookie: String = ""
        private set

    fun init(cookieMap: Map<String, String>) {
        this.cookieMap = buildMap {
            putAll(cookieMap)
            putAll(NeteaseClientConfig.cookieOverrides)
        }
        this.cookie = this.cookieMap.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    fun clear() {
        cookieMap = emptyMap()
        cookie = ""
    }

    fun getCookieMap(): Map<String, String> = cookieMap

    fun isLoggedIn(): Boolean = cookieMap.containsKey(CookieKeys.MUSIC_U)
}
