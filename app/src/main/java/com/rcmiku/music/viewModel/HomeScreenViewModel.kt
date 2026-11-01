package com.rcmiku.music.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rcmiku.music.utils.FavoriteSongIdsUtil
import com.rcmiku.music.utils.dataStore
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.api.recommend.RecommendApi
import com.rcmiku.ncmapi.model.DailySongsResponse
import com.rcmiku.ncmapi.model.RecommendPlaylistResponse
import com.rcmiku.ncmapi.utils.json
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(@ApplicationContext private val context: Context) :
    ViewModel() {

    private val cachedRecommendSongsKey = stringPreferencesKey("cachedRecommendSongs")
    private val cachedRecommendPlaylistKey = stringPreferencesKey("cachedRecommendPlaylist")

    private val _recommendSongs =
        MutableStateFlow<Result<DailySongsResponse>?>(null)
    val recommendSongs: StateFlow<Result<DailySongsResponse>?> =
        _recommendSongs.asStateFlow()
    private val _recommendPlaylist =
        MutableStateFlow<Result<RecommendPlaylistResponse>?>(null)
    val recommendPlaylist: StateFlow<Result<RecommendPlaylistResponse>?> =
        _recommendPlaylist.asStateFlow()

    private fun fetchRecommendSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = RecommendApi.recommendSongs()
            result.onSuccess { response ->
                _recommendSongs.value = result
            }
            if (result.isFailure && _recommendSongs.value == null) {
                _recommendSongs.value = result
            }
        }
    }

    private fun fetchFavoriteSongIds() {
        viewModelScope.launch(Dispatchers.IO) {
            AccountApi.favoriteSongIds().getOrNull()?.ids?.let {
                FavoriteSongIdsUtil.updateSongIds(context, it)
            }
        }
    }

    private fun fetchRecommendPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = RecommendApi.recommendPlaylist()
            result.onSuccess { response ->
                _recommendPlaylist.value = result
            }
            if (result.isFailure && _recommendPlaylist.value == null) {
                _recommendPlaylist.value = result
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data.map{it[com.rcmiku.music.constants.ncmCookieKey].orEmpty()}.distinctUntilChanged().collectLatest { cookie ->
                if(cookie.isNotEmpty())runCatching{com.rcmiku.ncmapi.utils.CookieProvider.init(json.decodeFromString<Map<String,String>>(cookie))}
                _recommendSongs.value=null;_recommendPlaylist.value=null
                refresh()
                // Warm the public Explore destination after Home can start rendering.
                kotlinx.coroutines.delay(700)
                com.rcmiku.ncmapi.api.playlist.PlaylistApi.topList()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            com.rcmiku.ncmapi.api.ReadResponseCache.updates.collect { path ->
                when(path){"/recommend/songs"->fetchRecommendSongs();"/personalized"->fetchRecommendPlaylist()}
            }
        }
    }

    fun refresh() {
        fetchRecommendSongs()
        fetchRecommendPlaylist()
        fetchFavoriteSongIds()
    }

}
