package com.rcmiku.ncmapi.api.artist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object ArtistApi {
    suspend fun artistHeadInfo(artistId: Long): Result<ArtistHeadInfoResponse> =
        apiGet("/artist/detail", mapOf("id" to artistId))

    suspend fun artistTopSong(artistId: Long): Result<ArtistTopSong> =
        apiGet("/artist/top/song", mapOf("id" to artistId))

    suspend fun artistAlbum(id: Long, limit: Int, offset: Int): Result<ArtistAlbumResponse> =
        apiGet("/artist/album", mapOf("id" to id, "limit" to limit, "offset" to offset))
}
