package com.melo.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LibraryRepository(app)
    private val favoritesPrefs = app.getSharedPreferences("favorites", 0)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _favorites = MutableStateFlow(
        favoritesPrefs.getStringSet("ids", emptySet()).orEmpty(),
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { repository.scan() }.onSuccess { _tracks.value = it }
            _refreshing.value = false
        }
    }

    fun addFolder(uri: Uri) {
        repository.addFolder(uri)
        refresh()
    }

    fun toggleFavorite(id: String) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _favorites.value = next
        favoritesPrefs.edit().putStringSet("ids", next).apply()
    }
}
