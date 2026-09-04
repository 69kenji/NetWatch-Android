@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.netwatch.android

import android.os.Bundle
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private enum class VideoSizeMode(val label: String, val resizeMode: Int) {
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
    ORIGINAL("Original", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    SIXTEEN_NINE("16:9", AspectRatioFrameLayout.RESIZE_MODE_FILL);

    fun next(): VideoSizeMode = entries[(ordinal + 1) % entries.size]
}

private enum class SubtitleSize(val label: String, val scaleIndependentPixels: Float) {
    SMALL("Small", 16f),
    MEDIUM("Medium", 20f),
    LARGE("Large", 25f);

    fun next(): SubtitleSize = entries[(ordinal + 1) % entries.size]
}

private enum class SubtitleBackdrop(val label: String) {
    NONE("None"),
    SHADOW("Shadow"),
    BOX("Box");

    fun next(): SubtitleBackdrop = entries[(ordinal + 1) % entries.size]
}

private enum class SubtitleContrast(val label: String) {
    NORMAL("Normal"),
    HIGH("High");

    fun next(): SubtitleContrast = entries[(ordinal + 1) % entries.size]
}

private data class SubtitleAppearance(
    val size: SubtitleSize = SubtitleSize.MEDIUM,
    val backdrop: SubtitleBackdrop = SubtitleBackdrop.SHADOW,
    val contrast: SubtitleContrast = SubtitleContrast.NORMAL,
)

private data class PlayerTrackOption(
    val type: Int,
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var client: PinnedGatewayClient? = null
    private var profile: GatewayProfile? = null
    private var sessionId: String? = null
    private var networkState by mutableStateOf(PlayerNetworkState())
    private var subtitleFeedback by mutableStateOf<String?>(null)
    private var mediaItem: MediaItem? = null
    private var subtitleFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearCachedSubtitles()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        val savedProfile = CredentialVault(applicationContext).load()
        val id = intent.getStringExtra(EXTRA_SESSION_ID)
        if (savedProfile == null || id == null || !Regex("^[A-Za-z0-9_-]{32}$").matches(id)) { finish(); return }
        profile = savedProfile
        sessionId = id
        client = PinnedGatewayClient.forProfile(savedProfile)
        val dataSourceFactory = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(client!!.httpClient))
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build().also { exoPlayer ->
                mediaItem = buildMediaItem()
                exoPlayer.setMediaItem(mediaItem!!)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }

        setContent {
            MaterialTheme(darkColorScheme(primary = Color(0xFF7B61FF), background = Color.Black, surface = Color(0xFF101116))) {
                NetWatchPlayer(
                    player = requireNotNull(player),
                    profile = savedProfile,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "NetWatch" },
                    backdropPath = intent.getStringExtra(EXTRA_BACKDROP_PATH),
                    network = networkState,
                    subtitleFeedback = subtitleFeedback,
                    onBack = ::finish,
                    onSubtitles = ::loadEnglishSubtitle,
                )
            }
        }
        monitorStatus()
    }

    @Composable
    private fun NetWatchPlayer(
        player: ExoPlayer,
        profile: GatewayProfile,
        title: String,
        backdropPath: String?,
        network: PlayerNetworkState,
        subtitleFeedback: String?,
        onBack: () -> Unit,
        onSubtitles: () -> Unit,
    ) {
        var controlsVisible by remember { mutableStateOf(true) }
        var networkOpen by remember { mutableStateOf(false) }
        var tracksOpen by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(VideoSizeMode.ORIGINAL) }
        var resizeFeedback by remember { mutableStateOf<String?>(null) }
        var playing by remember { mutableStateOf(player.isPlaying) }
        var playbackState by remember { mutableStateOf(player.playbackState) }
        var renderedFirstFrame by remember { mutableStateOf(false) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var buffered by remember { mutableLongStateOf(0L) }
        var volume by remember { mutableFloatStateOf(player.volume) }
        var error by remember { mutableStateOf<String?>(null) }
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        var tracks by remember { mutableStateOf(trackOptions(player.currentTracks)) }
        var subtitleAppearance by remember { mutableStateOf(loadSubtitleAppearance()) }
        val menuOpen = networkOpen || tracksOpen
        val compactControls = LocalConfiguration.current.screenWidthDp < 520
        val preparing = error == null && (!renderedFirstFrame || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE)
        val consumeClicks = remember { MutableInteractionSource() }

        BackHandler {
            when {
                networkOpen -> networkOpen = false
                tracksOpen -> tracksOpen = false
                else -> onBack()
            }
        }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(state: Int) { playbackState = state }
                override fun onRenderedFirstFrame() { renderedFirstFrame = true }
                override fun onTracksChanged(value: Tracks) { tracks = trackOptions(value) }
                override fun onPlayerError(playerError: PlaybackException) { error = playerError.message ?: "Player unavailable" }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player) {
            while (isActive) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                buffered = player.bufferedPosition.coerceAtLeast(0L)
                delay(250)
            }
        }
        LaunchedEffect(controlsVisible, playing, menuOpen, preparing) {
            if (controlsVisible && playing && !menuOpen && !preparing) { delay(3_000); controlsVisible = false }
        }
        LaunchedEffect(mode, playerView) { playerView?.applyVideoSizeMode(mode, player) }
        LaunchedEffect(subtitleAppearance, playerView) { playerView?.applySubtitleAppearance(subtitleAppearance) }
        LaunchedEffect(resizeFeedback) { if (resizeFeedback != null) { delay(1_400); resizeFeedback = null } }

        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(
                interactionSource = consumeClicks,
                indication = null,
            ) {
                if (menuOpen) {
                    networkOpen = false
                    tracksOpen = false
                } else if (!preparing && error == null) {
                    if (playing) player.pause() else player.play()
                    controlsVisible = true
                }
            },
        ) {
            AndroidView(
                factory = { context -> PlayerView(context).apply {
                    this.player = player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = mode.resizeMode
                    applySubtitleAppearance(subtitleAppearance)
                    playerView = this
                } },
                update = { it.applyVideoSizeMode(mode, player) }, modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(preparing, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                PreparationOverlay(
                    title = title,
                    backdropPath = backdropPath,
                    profile = profile,
                    network = network,
                    networkOpen = networkOpen,
                    onNetwork = { networkOpen = !networkOpen },
                    onBack = onBack,
                )
            }

            error?.let { message ->
                Column(Modifier.align(Alignment.Center).background(Color(0xF0101018), RoundedCornerShape(14.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Player unavailable", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(message, color = Color.White.copy(.55f), fontSize = 11.sp)
                    Text("Back", Modifier.padding(top = 16.dp).clickable(onClick = onBack), color = Color(0xFFB7A9FF))
                }
            }

            AnimatedVisibility(controlsVisible && !preparing && error == null, Modifier.align(Alignment.TopCenter)) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent))).padding(horizontal = 18.dp, vertical = 18.dp)
                        .clickable(interactionSource = consumeClicks, indication = null) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(NetWatchPlayerIcons.Back, "Back to NetWatch", onClick = onBack)
                    Text(title, Modifier.weight(1f).padding(horizontal = 10.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            AnimatedVisibility(controlsVisible && !preparing && error == null, Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.92f)))).padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 16.dp)
                        .clickable(interactionSource = consumeClicks, indication = null) {},
                ) {
                    val max = duration.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = position.coerceAtMost(duration.coerceAtLeast(0L)).toFloat(),
                        onValueChange = { position = it.toLong() }, onValueChangeFinished = { player.seekTo(position) },
                        valueRange = 0f..max,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF7B61FF), inactiveTrackColor = Color.White.copy(.16f)),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SkipTenButton(backward = true) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) }
                        PlayerIconButton(if (playing) NetWatchPlayerIcons.Pause else NetWatchPlayerIcons.Play, if (playing) "Pause" else "Play", iconSize = 27) { if (playing) player.pause() else player.play() }
                        SkipTenButton(backward = false) { player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration.coerceAtLeast(0L))) }
                        PlayerIconButton(if (volume == 0f) NetWatchPlayerIcons.SoundOff else NetWatchPlayerIcons.SoundHigh, if (volume == 0f) "Unmute" else "Mute") {
                            volume = if (volume == 0f) 1f else 0f
                            player.volume = volume
                        }
                        if (!compactControls) Text("${formatTime(position)}  /  ${formatTime(duration)}", Modifier.padding(start = 5.dp), color = Color.White.copy(.58f), fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        PlayerIconButton(NetWatchPlayerIcons.Tracks, "Tracks") { tracksOpen = !tracksOpen; networkOpen = false }
                        PlayerIconButton(NetWatchPlayerIcons.Download, "Network") { networkOpen = !networkOpen; tracksOpen = false }
                        ResizeButton(mode, showLabel = !compactControls) {
                            mode = mode.next()
                            resizeFeedback = mode.label
                        }
                    }
                }
            }

            if (!preparing && networkOpen) {
                NetworkPopover(network, (buffered - position).coerceAtLeast(0L), Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 74.dp))
            }
            if (!preparing && tracksOpen) {
                TracksPopover(
                    tracks = tracks,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 74.dp),
                    onSelect = { selectTrack(player, it) },
                    onSubtitles = { tracksOpen = false; onSubtitles() },
                    onSubtitlesOff = { disableSubtitles(player) },
                    appearance = subtitleAppearance,
                    onAppearanceChange = { appearance ->
                        subtitleAppearance = appearance
                        saveSubtitleAppearance(appearance)
                    },
                )
            }
            subtitleFeedback?.let { FeedbackPill(it, Modifier.align(Alignment.Center)) }
            resizeFeedback?.let { FeedbackPill(it, Modifier.align(Alignment.Center)) }
        }
    }

    @Composable
    private fun PreparationOverlay(
        title: String,
        backdropPath: String?,
        profile: GatewayProfile,
        network: PlayerNetworkState,
        networkOpen: Boolean,
        onNetwork: () -> Unit,
        onBack: () -> Unit,
    ) {
        val progress = network.bufferProgress.takeIf { it > 0.0 && it < 100.0 }
        val pulse by rememberInfiniteTransition(label = "preparation").animateFloat(
            initialValue = .38f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "brand pulse",
        )
        val consumeClicks = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = consumeClicks, indication = null) { if (networkOpen) onNetwork() }) {
            GatewayArtwork(backdropPath, profile, Modifier.fillMaxSize(), ContentScale.Crop, showFallbackMark = false)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.48f), Color.Black.copy(.28f), Color.Black.copy(.72f)))))
            PlayerIconButton(NetWatchPlayerIcons.Back, "Back to NetWatch", Modifier.align(Alignment.TopStart).padding(18.dp), onClick = onBack)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.netwatch_icon), null,
                    Modifier.size(92.dp).graphicsLayer { alpha = if (progress == null) pulse else .92f },
                )
                Text(title, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(network.state.ifBlank { "Preparing" }, Modifier.padding(top = 5.dp), color = Color.White.copy(.55f), fontSize = 10.sp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { (progress / 100.0).toFloat() },
                        Modifier.padding(top = 14.dp).width(220.dp).height(3.dp).clip(CircleShape),
                        color = Color(0xFF7B61FF), trackColor = Color.White.copy(.16f),
                    )
                }
            }
            PlayerIconButton(NetWatchPlayerIcons.Download, "Connection", Modifier.align(Alignment.BottomEnd).padding(18.dp), onClick = onNetwork)
            if (networkOpen) NetworkPopover(network, 0L, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 66.dp))
        }
    }

    @Composable
    private fun PlayerIconButton(
        icon: ImageVector,
        description: String,
        modifier: Modifier = Modifier,
        iconSize: Int = 22,
        onClick: () -> Unit,
    ) {
        Box(modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White.copy(.86f), modifier = Modifier.size(iconSize.dp))
        }
    }

    @Composable
    private fun SkipTenButton(backward: Boolean, onClick: () -> Unit) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(
                NetWatchPlayerIcons.SkipArrow,
                contentDescription = if (backward) "Back 10 seconds" else "Forward 10 seconds",
                tint = Color.White.copy(.86f),
                modifier = Modifier.size(23.dp).graphicsLayer { if (backward) scaleX = -1f },
            )
            Text("10", color = Color.White.copy(.86f), fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun ResizeButton(mode: VideoSizeMode, showLabel: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.height(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NetWatchPlayerIcons.Resize, contentDescription = "Resize video: ${mode.label}", tint = Color.White.copy(.86f), modifier = Modifier.size(20.dp))
            if (showLabel) Text(mode.label, Modifier.padding(start = 5.dp), color = Color.White.copy(.62f), fontSize = 9.sp)
        }
    }

    @Composable
    private fun NetworkPopover(network: PlayerNetworkState, bufferedVideo: Long, modifier: Modifier = Modifier) {
        val consumeClicks = remember { MutableInteractionSource() }
        Surface(modifier.width(270.dp).clickable(interactionSource = consumeClicks, indication = null) {}, color = Color(0xF5101018), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color.White.copy(.08f))) {
            Column(Modifier.padding(14.dp)) {
                Text("Network", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                NetworkValue("State", network.state)
                NetworkValue("Buffer", if (network.bufferProgress > 0) "${network.bufferProgress.toInt()}%" else "—")
                NetworkValue("Peers", network.peers.toString())
                if (bufferedVideo > 0) NetworkValue("Buffered video", formatTime(bufferedVideo))
            }
        }
    }

    @Composable
    private fun TracksPopover(
        tracks: List<PlayerTrackOption>,
        modifier: Modifier,
        onSelect: (PlayerTrackOption) -> Unit,
        onSubtitles: () -> Unit,
        onSubtitlesOff: () -> Unit,
        appearance: SubtitleAppearance,
        onAppearanceChange: (SubtitleAppearance) -> Unit,
    ) {
        val consumeClicks = remember { MutableInteractionSource() }
        val maximumHeight = (LocalConfiguration.current.screenHeightDp.dp - 106.dp).coerceAtLeast(220.dp)
        Surface(modifier.width(310.dp).heightIn(max = maximumHeight).clickable(interactionSource = consumeClicks, indication = null) {}, color = Color(0xF5101018), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color.White.copy(.08f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                Text("Tracks", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("AUDIO", Modifier.padding(top = 13.dp, bottom = 4.dp), color = Color.White.copy(.34f), fontSize = 8.sp)
                val audioTracks = tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.take(4)
                if (audioTracks.isEmpty()) TrackRow("Auto", true) {}
                else audioTracks.forEach { option -> TrackRow(option.label, option.selected) { onSelect(option) } }
                Text("SUBTITLES", Modifier.padding(top = 13.dp, bottom = 4.dp), color = Color.White.copy(.34f), fontSize = 8.sp)
                TrackRow("Off", false, onSubtitlesOff)
                tracks.filter { it.type == C.TRACK_TYPE_TEXT }.take(4).forEach { option -> TrackRow(option.label, option.selected) { onSelect(option) } }
                TrackRow("Find English", false, onSubtitles)
                Text("APPEARANCE", Modifier.padding(top = 13.dp, bottom = 4.dp), color = Color.White.copy(.34f), fontSize = 8.sp)
                AppearanceRow("Size", appearance.size.label) { onAppearanceChange(appearance.copy(size = appearance.size.next())) }
                AppearanceRow("Background", appearance.backdrop.label) { onAppearanceChange(appearance.copy(backdrop = appearance.backdrop.next())) }
                AppearanceRow("Contrast", appearance.contrast.label) { onAppearanceChange(appearance.copy(contrast = appearance.contrast.next())) }
                if (appearance != SubtitleAppearance()) {
                    TrackRow("Reset appearance", false) { onAppearanceChange(SubtitleAppearance()) }
                }
            }
        }
    }

    @Composable
    private fun AppearanceRow(label: String, value: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = Color.White.copy(.72f), fontSize = 10.sp)
            Text(value, color = Color(0xFFB7A9FF), fontSize = 9.sp)
        }
    }

    @Composable
    private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = if (selected) Color(0xFFB7A9FF) else Color.White.copy(.72f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Text("Selected", color = Color(0xFFB7A9FF), fontSize = 8.sp)
        }
    }

    @Composable
    private fun NetworkValue(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(.42f), fontSize = 9.sp)
            Text(value, color = Color.White.copy(.78f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun FeedbackPill(message: String, modifier: Modifier = Modifier) {
        Surface(modifier, color = Color(0xE609090F), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color.White.copy(.08f))) {
            Text(message, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Color.White.copy(.82f), fontSize = 10.sp)
        }
    }

    private fun trackOptions(tracks: Tracks): List<PlayerTrackOption> = buildList {
        tracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) return@forEach
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val fallback = if (group.type == C.TRACK_TYPE_AUDIO) "Audio ${index + 1}" else "Subtitle ${index + 1}"
                add(PlayerTrackOption(group.type, group, index, format.label ?: format.language?.uppercase() ?: fallback, group.isTrackSelected(index)))
            }
        }
    }

    private fun selectTrack(player: Player, option: PlayerTrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, listOf(option.index)))
            .build()
    }

    private fun disableSubtitles(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    private fun buildMediaItem(subtitle: MediaItem.SubtitleConfiguration? = null): MediaItem {
        val gateway = requireNotNull(client)
        val id = requireNotNull(sessionId)
        return MediaItem.Builder().setUri(gateway.absoluteUrl("/remote/v1/playback/$id/stream")).setSubtitleConfigurations(listOfNotNull(subtitle)).build()
    }

    private fun monitorStatus() {
        lifecycleScope.launch {
            while (isActive) {
                try {
                    val status = withContext(Dispatchers.IO) { client!!.get("/remote/v1/playback/${sessionId!!}") }
                    networkState = PlayerNetworkState(
                        state = status.optString("message", status.optString("state", "Preparing")),
                        bufferProgress = status.optDouble("buffer_progress", 0.0).coerceIn(0.0, 100.0),
                        peers = status.optInt("connected_peers"),
                    )
                } catch (_: Exception) { networkState = networkState.copy(state = "Unavailable") }
                delay(1_000)
            }
        }
    }

    private fun loadEnglishSubtitle() {
        val gateway = client ?: return
        val id = sessionId ?: return
        subtitleFeedback = "Finding subtitles…"
        lifecycleScope.launch {
            try {
                val subtitleAsset = withContext(Dispatchers.IO) {
                    val search = gateway.get("/remote/v1/playback/$id/subtitles?languages=en")
                    val first = search.optJSONArray("results")?.optJSONObject(0) ?: throw GatewayException("NOT_FOUND", "No English subtitles found")
                    val downloaded = gateway.post("/remote/v1/playback/$id/subtitles", JSONObject().put("subtitle_ref", first.getString("subtitle_ref")))
                    val filename = downloaded.optString("filename", "subtitle.srt")
                    val extension = filename.substringAfterLast('.', "srt").lowercase().takeIf { it in setOf("srt", "vtt", "ass", "ssa") } ?: "srt"
                    val file = File(cacheDir, "netwatch-subtitle-$id-${System.currentTimeMillis()}.$extension")
                    try {
                        file.writeBytes(gateway.getBytes(downloaded.getString("content_path")))
                    } catch (error: Exception) {
                        file.delete()
                        throw error
                    }
                    file
                }
                val mime = when (subtitleAsset.extension.lowercase()) { "vtt" -> MimeTypes.TEXT_VTT; "ass", "ssa" -> MimeTypes.TEXT_SSA; else -> MimeTypes.APPLICATION_SUBRIP }
                val subtitle = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(subtitleAsset))
                    .setMimeType(mime).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                val position = player?.currentPosition ?: 0L
                subtitleFile?.delete()
                subtitleFile = subtitleAsset
                mediaItem = buildMediaItem(subtitle)
                player?.setMediaItem(mediaItem!!, position)
                player?.prepare()
                player?.trackSelectionParameters = player?.trackSelectionParameters?.buildUpon()
                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    ?.setPreferredTextLanguage("en")
                    ?.setSelectUndeterminedTextLanguage(true)
                    ?.build() ?: return@launch
                player?.playWhenReady = true
                subtitleFeedback = "English subtitles loaded"
            } catch (error: Exception) { subtitleFeedback = error.message ?: "Subtitles unavailable" }
            delay(2_000)
            subtitleFeedback = null
        }
    }

    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        subtitleFile?.delete()
        subtitleFile = null
        clearCachedSubtitles()
        val gateway = client
        val id = sessionId
        if (isFinishing && gateway != null && id != null) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { gateway.delete("/remote/v1/playback/$id") } }
        super.onDestroy()
    }

    private fun clearCachedSubtitles() {
        cacheDir.listFiles { file -> file.isFile && file.name.startsWith("netwatch-subtitle-") }
            ?.forEach { file -> file.delete() }
    }

    private fun loadSubtitleAppearance(): SubtitleAppearance {
        val preferences = getSharedPreferences(SUBTITLE_PREFERENCES, MODE_PRIVATE)
        return SubtitleAppearance(
            size = enumValueOrDefault(preferences.getString("size", null), SubtitleSize.MEDIUM),
            backdrop = enumValueOrDefault(preferences.getString("backdrop", null), SubtitleBackdrop.SHADOW),
            contrast = enumValueOrDefault(preferences.getString("contrast", null), SubtitleContrast.NORMAL),
        )
    }

    private fun saveSubtitleAppearance(appearance: SubtitleAppearance) {
        getSharedPreferences(SUBTITLE_PREFERENCES, MODE_PRIVATE).edit()
            .putString("size", appearance.size.name)
            .putString("backdrop", appearance.backdrop.name)
            .putString("contrast", appearance.contrast.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun PlayerView.applySubtitleAppearance(appearance: SubtitleAppearance) {
        val highContrast = appearance.contrast == SubtitleContrast.HIGH
        val edgeType = when {
            highContrast -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            appearance.backdrop == SubtitleBackdrop.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        val background = if (appearance.backdrop == SubtitleBackdrop.BOX) {
            android.graphics.Color.argb(if (highContrast) 230 else 185, 0, 0, 0)
        } else {
            android.graphics.Color.TRANSPARENT
        }
        subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, appearance.size.scaleIndependentPixels)
            setBottomPaddingFraction(.08f)
            setStyle(
                CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    background,
                    android.graphics.Color.TRANSPARENT,
                    edgeType,
                    android.graphics.Color.BLACK,
                    null,
                ),
            )
        }
    }

    private fun PlayerView.applyVideoSizeMode(mode: VideoSizeMode, player: Player) {
        val videoSize = player.videoSize
        val nativeAspect = if (videoSize.height > 0) videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height else 0f
        resizeMode = if (mode == VideoSizeMode.SIXTEEN_NINE) AspectRatioFrameLayout.RESIZE_MODE_FIT else mode.resizeMode
        findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            ?.setAspectRatio(if (mode == VideoSizeMode.SIXTEEN_NINE) 16f / 9f else nativeAspect)
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
    }

    companion object {
        private const val SUBTITLE_PREFERENCES = "netwatch_subtitle_appearance"
        const val EXTRA_SESSION_ID = "playback_session_id"
        const val EXTRA_TITLE = "playback_title"
        const val EXTRA_BACKDROP_PATH = "playback_backdrop_path"
    }
}

private data class PlayerNetworkState(
    val state: String = "Preparing",
    val bufferProgress: Double = 0.0,
    val peers: Int = 0,
)
