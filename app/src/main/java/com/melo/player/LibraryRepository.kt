package com.melo.player

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LibraryRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val supportedExtensions = setOf("mp3", "aac", "m4a")

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = linkedMapOf<String, Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    if (!isSupported(name)) continue
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    )
                    tracks[uri.toString()] = Track(
                        id = uri.toString(),
                        uri = uri,
                        title = cursor.getString(titleIndex).cleanMetadata(nameWithoutExtension(name)),
                        artist = cursor.getString(artistIndex).cleanMetadata(""),
                        album = cursor.getString(albumIndex).cleanMetadata(""),
                        durationMs = cursor.getLong(durationIndex),
                        sizeBytes = cursor.getLong(sizeIndex),
                    )
                }
            }
        }

        prefs.getStringSet("folders", emptySet()).orEmpty().forEach { encoded ->
            runCatching { scanFolder(Uri.parse(encoded), tracks) }
        }

        tracks.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun addFolder(uri: Uri) {
        val folders = prefs.getStringSet("folders", emptySet()).orEmpty().toMutableSet()
        folders += uri.toString()
        prefs.edit().putStringSet("folders", folders).apply()
    }

    private fun scanFolder(uri: Uri, out: MutableMap<String, Track>) {
        DocumentFile.fromTreeUri(context, uri)?.let { scanDocument(it, out) }
    }

    private fun scanDocument(document: DocumentFile, out: MutableMap<String, Track>) {
        if (document.isDirectory) {
            document.listFiles().forEach { scanDocument(it, out) }
            return
        }

        val name = document.name.orEmpty()
        if (!document.isFile || !isSupported(name)) return

        val meta = readMetadata(document.uri)
        out[document.uri.toString()] = Track(
            id = document.uri.toString(),
            uri = document.uri,
            title = meta.title.cleanMetadata(nameWithoutExtension(name)),
            artist = meta.artist.cleanMetadata(""),
            album = meta.album.cleanMetadata(""),
            durationMs = meta.durationMs,
            sizeBytes = document.length().coerceAtLeast(0L),
        )
    }

    private fun readMetadata(uri: Uri): Metadata = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull() ?: 0L,
            )
        }
    }.getOrDefault(Metadata())

    private fun isSupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions

    private fun nameWithoutExtension(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { "רצועה ללא שם" }

    private data class Metadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
    )
}

private fun String?.cleanMetadata(fallback: String): String =
    if (this.isNullOrBlank() || this.equals("<unknown>", ignoreCase = true)) fallback else this
