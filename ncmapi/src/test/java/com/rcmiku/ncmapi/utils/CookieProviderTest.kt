package com.rcmiku.ncmapi.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieProviderTest {
    @After
    fun tearDown() {
        CookieProvider.clear()
    }

    @Test
    fun initKeepsLoginCookieAndAppliesAndroidFingerprint() {
        CookieProvider.init(
            mapOf(
                CookieKeys.MUSIC_U to "token",
                CookieKeys.OS to "pc",
                CookieKeys.APP_VER to "old"
            )
        )

        val cookie = CookieProvider.getCookieMap()
        assertEquals("token", cookie[CookieKeys.MUSIC_U])
        assertEquals("android", cookie[CookieKeys.OS])
        assertEquals(NeteaseClientConfig.APP_VERSION, cookie[CookieKeys.APP_VER])
        assertEquals("xiaomi", cookie[CookieKeys.CHANNEL])
        assertEquals("6006066", cookie[CookieKeys.VERSION_CODE])
        assertEquals("2268x1080", cookie[CookieKeys.RESOLUTION])
        assertTrue(CookieProvider.isLoggedIn())
    }

    @Test
    fun clearRemovesAuthenticationState() {
        CookieProvider.init(mapOf(CookieKeys.MUSIC_U to "token"))

        CookieProvider.clear()

        assertTrue(CookieProvider.getCookieMap().isEmpty())
        assertTrue(CookieProvider.cookie.isEmpty())
        assertFalse(CookieProvider.isLoggedIn())
    }
}
