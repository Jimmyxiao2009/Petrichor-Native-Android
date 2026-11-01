package com.rcmiku.music.utils

import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import java.io.File
import kotlinx.coroutines.launch

object SongListUtil {

    private val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
    private val writes=kotlinx.coroutines.channels.Channel<List<Song>>(kotlinx.coroutines.channels.Channel.CONFLATED)
    init { scope.launch {for(snapshot in writes){runCatching{val target=File(file,SONG_LIST);val temp=File(file,"$SONG_LIST.tmp");temp.writeText(json.encodeToString(snapshot));if(!temp.renameTo(target)){target.writeText(temp.readText());temp.delete()}}}} }
    private const val SONG_LIST = "song_list.json"
    private var file: File? = null
    private var songList: List<Song> = emptyList()

    fun init(file: File) {
        if (!file.exists()) file.mkdir()
        this.file = file
    }

    fun saveSong(song: Song) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.add(song)
        saveSongList(updatedSongList)
    }

    fun removeSong(songId: Long) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.removeIf { it.id == songId }
        songList = updatedSongList
        saveSongList(updatedSongList)
    }

    fun saveSong(song: Song, index: Int) {
        val updatedSongList = songList.toMutableList()
        updatedSongList.add(index, song)
        saveSongList(updatedSongList)
    }


    fun saveSongList(songList: List<Song>) {
        this.songList = songList
        writes.trySend(songList.toList())
    }

    fun loadSongList(): List<Song>? {
        val songListJson = File(file, SONG_LIST)
        return if (songListJson.exists()) {
            songList = runCatching{json.decodeFromString<List<Song>>(songListJson.readText())}.getOrDefault(emptyList())
            return songList
        } else {
            null
        }
    }
}
