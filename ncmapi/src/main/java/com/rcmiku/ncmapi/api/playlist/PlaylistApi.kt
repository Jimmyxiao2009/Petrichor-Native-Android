package com.rcmiku.ncmapi.api.playlist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object PlaylistApi {
    suspend fun playlistDetail(id: Long, limit: Int): Result<PlaylistDetailResponse> =
        apiGet("/playlist/detail", mapOf("id" to id))

    suspend fun playlistV6Detail(id: Long): Result<PlaylistDetailResponse> =
        apiGet("/playlist/detail", mapOf("id" to id))

    suspend fun playlistInfo(id: Long): Result<PlaylistInfoResponse> =
        apiGet("/playlist/detail/dynamic", mapOf("id" to id))

    suspend fun topList(): Result<TopListResponse> =
        apiGet("/toplist")

    suspend fun playlistSub(id: Long, isSub: Boolean): Result<ApiCodeResponse> {
        val t = if (isSub) 1 else 2
        return apiGet<ApiCodeResponse>(
            "/playlist/subscribe",
            mapOf("id" to id, "t" to t)
        ).mapCatching {
            if (it.code == 200) {
                it
            } else {
                val reason = it.message?.takeIf(String::isNotBlank)
                    ?: it.msg?.takeIf(String::isNotBlank)
                    ?: "服务端返回业务码 ${it.code}"
                throw IllegalStateException(reason)
            }
        }
    }
}
