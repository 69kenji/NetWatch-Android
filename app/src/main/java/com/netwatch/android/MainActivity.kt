package com.netwatch.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val Ink = Color(0xFF08090C)
private val Panel = Color(0xFF101116)
private val PanelRaised = Color(0xFF15151C)
private val Accent = Color(0xFF7B61FF)
private val TextPrimary = Color(0xFFE5E3E9)
private val TextSecondary = Color(0xFF96909E)
private val TextMuted = Color(0xFF68636F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = GatewayRepository(CredentialVault(applicationContext))
        setContent {
            NetWatchTheme {
                val model: MainViewModel = viewModel(factory = simpleViewModelFactory { MainViewModel(repository) })
                NetWatchApp(model) { _, session, title, backdropPath ->
                    startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_SESSION_ID, session.id)
                        .putExtra(PlayerActivity.EXTRA_TITLE, title)
                        .putExtra(PlayerActivity.EXTRA_BACKDROP_PATH, backdropPath))
                }
            }
        }
    }
}

@Composable
private fun NetWatchApp(model: MainViewModel, openPlayer: (GatewayProfile, PlaybackSession, String, String?) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (state.profile != null && state.selected == null) {
                NetWatchNavigation(state.view, model::showView)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when {
                state.profile == null -> PairingScreen(model::pair)
                state.selected != null -> DetailsScreen(state, model, openPlayer)
                else -> CatalogScreen(state, model)
            }
            if (state.loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            state.error?.let { message ->
                AlertDialog(
                    onDismissRequest = model::clearError,
                    confirmButton = { TextButton(onClick = model::clearError) { Text("OK", color = Accent) } },
                    title = { Text("NetWatch") }, text = { Text(message) }, containerColor = PanelRaised,
                )
            }
        }
    }
}

@Composable
private fun NetWatchNavigation(current: CatalogView, onSelect: (CatalogView) -> Unit) {
    Surface(color = Color(0xF20D0E12), shadowElevation = 16.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            listOf(
                Triple(CatalogView.HOME, NetWatchAppIcons.Home, "Home"),
                Triple(CatalogView.DISCOVER, NetWatchAppIcons.Discover, "Discover"),
                Triple(CatalogView.SETTINGS, NetWatchAppIcons.Settings, "Settings"),
            ).forEach { (view, icon, label) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(view) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = if (current == view) Accent else TextMuted, modifier = Modifier.size(21.dp))
                    Text(label, color = if (current == view) TextPrimary else TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PairingScreen(onPair: (String) -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.netwatch_icon), null, Modifier.size(92.dp))
        Text("NetWatch", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Text("Pair device", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { scanning = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Scan pairing QR") }
        if (scanning) {
            Spacer(Modifier.height(18.dp))
            QrScanner(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(14.dp)), { scanning = false; onPair(it) }) { scanning = false }
        }
    }
}

@Composable
private fun CatalogScreen(state: MainUiState, model: MainViewModel) {
    val profile = requireNotNull(state.profile)
    when (state.view) {
        CatalogView.HOME -> HomeScreen(state, profile, model)
        CatalogView.DISCOVER -> DiscoverScreen(state, profile, model)
        CatalogView.SEARCH -> SearchScreen(state, profile, model)
        CatalogView.SETTINGS -> SettingsScreen(state, model)
    }
}

@Composable
private fun BrandHeader(subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.netwatch_icon), null, Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("NetWatch", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HomeScreen(state: MainUiState, profile: GatewayProfile, model: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
        item { BrandHeader() }
        item { SearchField("Search movies, TV, anime…", "", { model.search(it) }) }
        if (state.homeSections.isEmpty() && !state.loading) item { EmptyState("Catalog unavailable", "The PC did not return any titles.") }
        state.homeSections.forEach { section ->
            item {
                Text(section.title, Modifier.padding(start = 18.dp, top = 24.dp, bottom = 10.dp), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(section.items, key = { it.catalogId }) { CatalogCard(it, profile, Modifier.width(126.dp)) { model.select(it) } }
                }
            }
        }
        item { Text("Metadata · TMDB", Modifier.fillMaxWidth().padding(top = 26.dp), color = TextMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun DiscoverScreen(state: MainUiState, profile: GatewayProfile, model: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Discover")
        SearchField("Search movies, TV, anime…", "", { model.search(it) })
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
            val gap = 8.dp
            val unit = (maxWidth - gap * 2) / 3.25f
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                CompactSelect(
                    label = "Catalog",
                    options = listOf("movies" to "Movies", "tv" to "TV", "anime" to "Anime"),
                    selected = state.discoverMedia,
                    width = unit,
                ) { model.discover(it, state.discoverCategory, null) }
                CompactSelect(
                    label = "Category",
                    options = listOf("popular" to "Popular", "new" to "New", "featured" to "Featured"),
                    selected = state.discoverCategory,
                    width = unit,
                ) { model.discover(state.discoverMedia, it, state.discoverGenre) }
                CompactSelect(
                    label = "Genre",
                    options = listOf("top" to "Top") + state.discoverGenres.map { it.id.toString() to it.name },
                    selected = state.discoverGenre?.toString() ?: "top",
                    width = unit * 1.25f,
                ) {
                    model.discover(state.discoverMedia, state.discoverCategory, it.takeUnless { value -> value == "top" }?.toIntOrNull())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.loading) "Loading" else "${state.items.size} titles", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val genreLabel = state.discoverGenres.firstOrNull { it.id == state.discoverGenre }?.name ?: "Top"
            Text("${state.discoverMedia.label()} · ${state.discoverCategory.replaceFirstChar(Char::uppercase)} · $genreLabel", color = TextMuted, fontSize = 10.sp)
        }
        CatalogGrid(state.items, profile, model::select, Modifier.weight(1f))
    }
}

@Composable
private fun CompactSelect(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    width: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Column(Modifier.width(width)) {
        Text(label.uppercase(), Modifier.padding(start = 2.dp, bottom = 6.dp), color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        Surface(
            Modifier.fillMaxWidth().height(38.dp).clickable { expanded = true },
            color = Panel,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.085f)),
        ) {
            Row(Modifier.fillMaxSize().padding(start = 11.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedLabel, Modifier.weight(1f), color = TextPrimary.copy(.9f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(NetWatchAppIcons.NavArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(width).heightIn(max = 320.dp).background(PanelRaised),
            ) {
                options.forEach { (value, copy) ->
                    DropdownMenuItem(
                        text = { Text(copy, color = if (value == selected) Color(0xFFB7A9FF) else TextPrimary, fontSize = 11.sp) },
                        onClick = {
                            expanded = false
                            if (value != selected) onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(state: MainUiState, profile: GatewayProfile, model: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Search")
        SearchField("Search movies, TV, anime…", state.searchQuery, model::search)
        if (state.searchQuery.isBlank()) EmptyState("Find something to watch", "Search movies, TV, and anime from your PC.")
        else {
            Text("${state.items.size} results", Modifier.padding(18.dp), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            CatalogGrid(state.items, profile, model::select, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, model: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Settings")
        Text("REMOTE ACCESS", Modifier.padding(start = 18.dp, top = 24.dp, bottom = 8.dp), color = TextMuted, fontSize = 9.sp)
        Surface(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), color = Panel, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Remote access", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("${state.profile?.host}:${state.profile?.port}", color = TextSecondary, fontSize = 12.sp)
                Text(if (state.runtimeReady) "Ready" else "Unavailable", color = if (state.runtimeReady) Color(0xFF7FC79E) else Color(0xFFEF8D96), fontSize = 10.sp)
            }
        }
        TextButton(onClick = model::disconnect, Modifier.padding(10.dp)) { Text("Unpair this device", color = Color(0xFFEF8D96)) }
    }
}

@Composable
private fun SearchField(placeholder: String, initial: String, onSearch: (String) -> Unit) {
    var query by remember(initial) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(160) }, singleLine = true,
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 12.sp) },
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        TextButton(onClick = { onSearch(query) }) { Text("Search", color = Accent) }
    }
}

@Composable
private fun ChoiceRow(values: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { (value, label) ->
            Surface(
                modifier = Modifier.clickable { onSelect(value) },
                color = if (value == selected) Accent.copy(alpha = .18f) else Panel,
                shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (value == selected) Accent.copy(alpha = .6f) else Color.White.copy(alpha = .06f)),
            ) { Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = if (value == selected) Color(0xFFB7A9FF) else TextSecondary, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun CatalogGrid(items: List<CatalogItem>, profile: GatewayProfile, onSelect: (CatalogItem) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) { EmptyState("No titles in this selection", "Try another category or search."); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp), modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { items(items, key = { it.catalogId }) { CatalogCard(it, profile, Modifier.fillMaxWidth(), onSelect) } }
}

@Composable
private fun CatalogCard(item: CatalogItem, profile: GatewayProfile, modifier: Modifier = Modifier, onSelect: (CatalogItem) -> Unit) {
    Column(modifier.clickable { onSelect(item) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(9.dp)).background(PanelRaised)) {
            GatewayArtwork(item.artworkPath, profile, Modifier.fillMaxSize())
            Text(item.kindLabel, Modifier.align(Alignment.TopStart).padding(7.dp).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp), color = TextPrimary, fontSize = 8.sp)
        }
        Text(item.title, Modifier.padding(top = 7.dp), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(item.year, item.language?.uppercase()).joinToString(" · ").ifBlank { item.kindLabel }, color = TextMuted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun DetailsScreen(state: MainUiState, model: MainViewModel, openPlayer: (GatewayProfile, PlaybackSession, String, String?) -> Unit) {
    val item = requireNotNull(state.selected)
    val profile = requireNotNull(state.profile)
    BackHandler(onBack = model::closeSelection)
    LazyColumn(Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                GatewayArtwork(item.backdropPath ?: item.artworkPath, profile, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.30f), Color.Black.copy(.42f), Ink))))
                TextButton(onClick = model::closeSelection, Modifier.statusBarsPadding().padding(8.dp)) { Text("‹  Back", color = Color.White, fontSize = 14.sp) }
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(PanelRaised)) { GatewayArtwork(item.artworkPath, profile, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.kindLabel, color = Color(0xFFB7A9FF), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.title, color = Color.White, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(item.year, item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, item.language?.uppercase()).joinToString(" · "), color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        item { item.overview?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp) } }
        if (item.isSeries) {
            item {
                Text("SEASONS", Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp), color = TextMuted, fontSize = 9.sp)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.seasons, key = { it.number }) { season ->
                        val selected = season.number == state.season
                        Surface(Modifier.clickable { model.selectSeason(season.number) }, color = if (selected) Accent.copy(.18f) else Panel, shape = RoundedCornerShape(9.dp)) {
                            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                                Text(season.name, color = if (selected) Color(0xFFB7A9FF) else TextPrimary, fontSize = 11.sp)
                                if (season.episodeCount > 0) Text("${season.episodeCount} episodes", color = TextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
                Text("EPISODES", Modifier.padding(start = 18.dp, top = 22.dp, bottom = 8.dp), color = TextMuted, fontSize = 9.sp)
            }
            items(state.episodes, key = { it.number }) { episode ->
                EpisodeRow(episode, state.episode == episode.number, profile) { model.selectEpisode(episode.number) }
            }
        }
        if (!item.isSeries || state.episode != null) {
            item {
                val selectedEpisode = state.episodes.firstOrNull { it.number == state.episode }
                Text("STREAMS", Modifier.padding(start = 18.dp, top = 24.dp, bottom = 4.dp), color = TextMuted, fontSize = 9.sp)
                Text(
                    if (state.loading) "Finding releases…" else "${state.options.size} options",
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                if (selectedEpisode != null) Text("S${state.season.toString().padStart(2, '0')}E${selectedEpisode.number.toString().padStart(2, '0')} · ${selectedEpisode.name}", Modifier.padding(horizontal = 18.dp), color = TextMuted, fontSize = 10.sp)
            }
            if (state.options.isEmpty() && !state.loading) item { EmptyState("No streams found", "Try another episode or source.") }
            items(state.options, key = { it.releaseRef }) { option -> StreamRow(option) { model.start(option, openPlayer) } }
        } else if (!state.loading) item { Text("Select an episode to find releases.", Modifier.padding(18.dp), color = TextMuted, fontSize = 11.sp) }
        item { Text("Metadata · TMDB", Modifier.fillMaxWidth().padding(top = 24.dp), color = TextMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeChoice, selected: Boolean, profile: GatewayProfile, onSelect: () -> Unit) {
    Surface(
        Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().clickable(onClick = onSelect),
        color = if (selected) Accent.copy(.10f) else Panel, shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent.copy(.45f) else Color.White.copy(.04f)),
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp)).background(PanelRaised)) { GatewayArtwork(episode.stillPath, profile, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("E${episode.number.toString().padStart(2, '0')}  ${episode.name}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(episode.runtime?.let { "$it min" }, episode.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = TextMuted, fontSize = 9.sp)
                episode.overview?.let { Text(it, color = TextSecondary, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Text("›", color = if (selected) Accent else TextMuted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun StreamRow(option: StreamOption, onPlay: () -> Unit) {
    Surface(Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().clickable(onClick = onPlay), color = Panel, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Accent.copy(.14f), CircleShape), contentAlignment = Alignment.Center) { Text("▶", color = Color(0xFFB7A9FF), fontSize = 13.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(option.title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(option.details, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${option.seeders} seeds", color = TextSecondary, fontSize = 9.sp)
                option.sizeLabel?.let { Text(it, color = TextMuted, fontSize = 8.sp) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◇", color = TextMuted, fontSize = 30.sp)
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(body, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
private fun QrScanner(modifier: Modifier, onQr: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Column(modifier.background(PanelRaised, RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("Camera access is used only while scanning the pairing QR code. Frames stay on this device.", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp)); Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }; return
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var retryKey by remember { mutableStateOf(0) }
    var cameraError by remember(retryKey) { mutableStateOf<String?>(null) }
    val previewView = remember(retryKey) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        cameraError?.let {
            Column(
                Modifier.fillMaxSize().background(PanelRaised).padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera unavailable", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Check camera access and try again.", color = TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { retryKey += 1 }) { Text("Retry") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
    DisposableEffect(lifecycleOwner, previewView, retryKey) {
        val executor = Executors.newSingleThreadExecutor()
        val delivered = AtomicBoolean(false)
        val analyzing = AtomicBoolean(false)
        val disposed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var scanner = runCatching {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        }.onFailure { error ->
            Log.e("NetWatchCamera", "Unable to initialize barcode scanning", error)
            cameraError = "Camera unavailable"
        }.getOrNull()

        runCatching { ProcessCameraProvider.getInstance(context) }
            .onSuccess { providerFuture ->
                providerFuture.addListener({
                    if (disposed.get()) return@addListener
                    runCatching {
                        val resolvedProvider = providerFuture.get()
                        val selector = when {
                            resolvedProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                            resolvedProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                            else -> throw IllegalStateException("No camera is available")
                        }
                        val barcodeScanner = scanner ?: throw IllegalStateException("Barcode scanner is unavailable")
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val image = imageProxy.image
                            if (image == null || delivered.get() || !analyzing.compareAndSet(false, true)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            runCatching {
                                barcodeScanner.process(InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees))
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstNotNullOfOrNull { it.rawValue }?.let { value ->
                                            if (delivered.compareAndSet(false, true)) onQr(value)
                                        }
                                    }
                                    .addOnFailureListener { error -> Log.w("NetWatchCamera", "Unable to read camera frame", error) }
                                    .addOnCompleteListener {
                                        analyzing.set(false)
                                        imageProxy.close()
                                    }
                            }.onFailure { error ->
                                analyzing.set(false)
                                imageProxy.close()
                                Log.w("NetWatchCamera", "Unable to submit camera frame", error)
                            }
                        }
                        resolvedProvider.unbindAll()
                        resolvedProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        provider = resolvedProvider
                        cameraError = null
                    }.onFailure { error ->
                        Log.e("NetWatchCamera", "Unable to start QR camera", error)
                        cameraError = "Camera unavailable"
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            .onFailure { error ->
                Log.e("NetWatchCamera", "Unable to create camera provider", error)
                cameraError = "Camera unavailable"
            }

        onDispose {
            disposed.set(true)
            runCatching { provider?.unbindAll() }
            scanner?.close()
            scanner = null
            executor.shutdownNow()
        }
    }
}

private fun String.label() = when (this) { "movies" -> "Movies"; "tv" -> "TV"; "anime" -> "Anime"; else -> replaceFirstChar(Char::uppercase) }

@Composable
private fun NetWatchTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel, onBackground = TextPrimary, onSurface = TextPrimary),
    content = content,
)

private inline fun <reified T : androidx.lifecycle.ViewModel> simpleViewModelFactory(crossinline create: () -> T) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <M : androidx.lifecycle.ViewModel> create(modelClass: Class<M>): M = create() as M
    }
