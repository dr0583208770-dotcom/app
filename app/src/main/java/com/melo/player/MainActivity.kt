package com.melo.player

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private var mediaController by mutableStateOf<MediaController?>(null)

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContent {
            MeloTheme {
                MeloPlayerApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            { runCatching { mediaController = future.get() } },
            mainExecutor,
        )
    }

    override fun onStop() {
        mediaController?.release()
        mediaController = null
        super.onStop()
    }

    private fun requestPermissionsIfNeeded() {
        val requested = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_AUDIO)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = requested.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray())
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MeloPlayerApp(vm: PlayerViewModel) {
        val tracks by vm.tracks.collectAsState()
        val favorites by vm.favorites.collectAsState()
        val refreshing by vm.refreshing.collectAsState()
        val controller = mediaController

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var query by rememberSaveable { mutableStateOf("") }
        var sortMode by rememberSaveable { mutableIntStateOf(0) }
        var ascending by rememberSaveable { mutableStateOf(true) }
        var shuffle by rememberSaveable { mutableStateOf(false) }
        var repeatMode by rememberSaveable { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
        var showNowPlaying by remember { mutableStateOf(false) }
        var sortMenu by remember { mutableStateOf(false) }
        var playerTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(controller) {
            while (controller != null) {
                delay(400)
                playerTick++
            }
        }

        LaunchedEffect(controller, shuffle, repeatMode) {
            controller?.shuffleModeEnabled = shuffle
            controller?.repeatMode = repeatMode
        }

        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.addFolder(uri)
        }

        val visibleTracks = remember(
            tracks, favorites, tab, query, sortMode, ascending, playerTick
        ) {
            val base = if (tab == 0) tracks
            else tracks.filter { favorites.contains(it.id) }

            val q = query.trim().lowercase(Locale.ROOT)
            val searched = if (q.isBlank()) base
            else base.filter { it.searchableText.contains(q) }

            val sorted = when (sortMode) {
                1 -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
                2 -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
                3 -> searched.sortedBy { it.durationMs }
                else -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
            if (ascending) sorted else sorted.reversed()
        }

        val current = controller?.currentMediaItem?.let { item ->
            tracks.firstOrNull { it.id == item.mediaId } ?: Track(
                id = item.mediaId,
                uri = item.localConfiguration?.uri ?: android.net.Uri.EMPTY,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                durationMs = controller.duration.coerceAtLeast(0L),
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (tab == 0) "הספרייה שלך" else "מועדפים",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                visibleTracks.size.toString() + " רצועות",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, "בחר תיקייה")
                        }
                        IconButton(onClick = vm::refresh, enabled = !refreshing) {
                            Icon(Icons.Default.Refresh, "רענן ספרייה")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null) {
                        current?.let { MiniPlayer(it, controller) { showNowPlaying = true } }
                    }
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    ) {
                        NavItem(
                            selected = tab == 0,
                            icon = Icons.Default.LibraryMusic,
                            text = "שירים",
                            onClick = { tab = 0 },
                            modifier = Modifier.weight(1f),
                        )
                        NavItem(
                            selected = tab == 1,
                            icon = Icons.Default.Favorite,
                            text = "מועדפים",
                            onClick = { tab = 1 },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            ) {
                SearchBar(query, { query = it })
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, "מיון")
                        }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("שם") },
                                onClick = { sortMode = 0; sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("אמן") },
                                onClick = { sortMode = 1; sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("אלבום") },
                                onClick = { sortMode = 2; sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("אורך") },
                                onClick = { sortMode = 3; sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(if (ascending) "סדר עולה" else "סדר יורד") },
                                onClick = { ascending = !ascending },
                            )
                        }
                    }
                    AssistChip(
                        onClick = { shuffle = !shuffle },
                        label = { Text(if (shuffle) "אקראי פעיל" else "אקראי") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Shuffle,
                                null,
                                tint = if (shuffle) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                        label = {
                            Text(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "חזרה: שיר"
                                    Player.REPEAT_MODE_ALL -> "חזרה: הכול"
                                    else -> "חזרה"
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Repeat, null) },
                    )
                }

                if (refreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    tracks.isEmpty() -> EmptyLibrary(
                        onChoose = { folderPicker.launch(null) },
                        onRefresh = vm::refresh,
                        refreshing = refreshing,
                    )
                    visibleTracks.isEmpty() -> EmptySearch()
                    else -> TrackList(
                        tracks = visibleTracks,
                        favorites = favorites,
                        controller = controller,
                        onFavorite = { vm.toggleFavorite(it.id) },
                        onPlay = { selected ->
                            playList(visibleTracks, selected, controller)
                        },
                    )
                }
            }
        }

        if (showNowPlaying && current != null) {
            NowPlaying(
                track = current,
                controller = controller,
                favorite = favorites.contains(current.id),
                onFavorite = { vm.toggleFavorite(current.id) },
                shuffle = shuffle,
                repeatMode = repeatMode,
                onShuffle = { shuffle = !shuffle },
                onRepeat = {
                    repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                },
                onDismiss = { showNowPlaying = false },
            )
        }
    }

    private fun playList(list: List<Track>, selected: Track, controller: MediaController?) {
        controller ?: return
        val items = list.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.displayArtist())
                        .setAlbumTitle(track.album)
                        .build(),
                )
                .build()
        }
        val start = list.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        controller.setMediaItems(items, start, 0L)
        controller.prepare()
        controller.play()
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                "חיפוש שיר, אמן או אלבום…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    favorites: Set<String>,
    controller: MediaController?,
    onFavorite: (Track) -> Unit,
    onPlay: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            val playing = controller?.currentMediaItem?.mediaId == track.id &&
                controller.isPlaying

            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(track) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (playing)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (playing) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            null,
                            tint = if (playing) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                        )
                        val subtitle = if (track.album.isBlank()) track.displayArtist()
                        else track.displayArtist() + "  •  " + track.album
                        Text(
                            subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        formatDuration(track.durationMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    IconButton(onClick = { onFavorite(track) }) {
                        Icon(
                            if (favorites.contains(track.id))
                                Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "מועדף",
                            tint = if (favorites.contains(track.id))
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, controller: MediaController?, onOpen: () -> Unit) {
    val playing = controller?.isPlaying == true
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).shadow(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(track.displayArtist(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "ניגון/השהיה")
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, "הבא")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(
    track: Track,
    controller: MediaController?,
    favorite: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(track.durationMs) }
    var slider by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        while (true) {
            val c = controller
            if (c != null && !dragging) {
                position = c.currentPosition.coerceAtLeast(0L)
                duration = c.duration.takeIf { it > 0L } ?: track.durationMs
                slider = if (duration > 0L) position.toFloat() / duration else 0f
            }
            delay(350)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    modifier = Modifier.size(110.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = slider.coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    slider = it
                    position = (it * duration.coerceAtLeast(1L)).toLong()
                },
                onValueChangeFinished = {
                    controller?.seekTo(position)
                    dragging = false
                },
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position), fontSize = 11.sp)
                Text(formatDuration(duration), fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        "אקראי",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                    Icon(Icons.Default.SkipPrevious, "הקודם", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
                    modifier = Modifier.size(70.dp),
                ) {
                    Icon(
                        if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "ניגון/השהיה",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, "הבא", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        Icons.Default.Repeat,
                        "חזרה",
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> "חזרה על שיר"
                                Player.REPEAT_MODE_ALL -> "חזרה על הרשימה"
                                else -> "חזרה כבויה"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Repeat, null) },
                )
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "מועדף",
                        tint = if (favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            text,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary(
    onChoose: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "עדיין אין כאן מוזיקה",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "סרוק את המכשיר או בחר תיקייה עם MP3, AAC או M4A.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onChoose) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("בחר תיקייה")
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("סרוק מחדש")
            }
        }
    }
}

@Composable
private fun EmptySearch() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("לא נמצאו רצועות", style = MaterialTheme.typography.titleMedium)
            Text("נסה שינוי בחיפוש.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return (total / 60L).toString() + ":" + (total % 60L).toString().padStart(2, '0')
}

@Composable
private fun MeloTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFB9A4FF),
        onPrimary = Color(0xFF21124C),
        primaryContainer = Color(0xFF4D397F),
        tertiary = Color(0xFF8AD7FF),
        background = Color(0xFF080A0F),
        surface = Color(0xFF0E1118),
        surfaceVariant = Color(0xFF1B202A),
        onSurface = Color(0xFFF0F1F5),
        onSurfaceVariant = Color(0xFFBFC3CE),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = dark,
            content = content,
        )
    }
}
