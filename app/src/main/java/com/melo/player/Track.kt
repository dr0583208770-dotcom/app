package com.melo.player

import android.net.Uri

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long = 0L,
) {
    val searchableText: String
        get() = (title + " " + artist + " " + album).lowercase()

    fun displayArtist(): String = artist.ifBlank { "אמן לא ידוע" }
}
