package com.source.player.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.components.BrowserEmptyState
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.oklchToColor
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel
import kotlin.math.abs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Content of the Player tab. The tab bar stays below it, so it lays out in the space
 * the Scaffold leaves (no system-bar padding of its own).
 *
 * @param onClose back to the tab the player was opened from; null when there is none
 * (app started on the player), and the close button is then hidden.
 * @param onBrowseFolders selects the Folders tab (empty state link).
 */
@Composable
fun PlayerScreen(
        navController: NavController,
        onClose: (() -> Unit)?,
        onBrowseFolders: () -> Unit,
        vm: PlayerViewModel = hiltViewModel(),
) {
    val song by vm.currentSong.collectAsStateWithLifecycle()
    // System Back does what the close button does.
    BackHandler(enabled = onClose != null) { onClose?.invoke() }

    if (song == null) {
        val hasTracks by vm.hasTracks.collectAsStateWithLifecycle()
        PlayerEmptyState(
                canShuffle = hasTracks,
                onShuffleAll = { vm.shuffleAll() },
                onBrowseFolders = onBrowseFolders,
        )
        return
    }
    NowPlaying(navController, onClose, vm)
}

@Composable
private fun NowPlaying(
        navController: NavController,
        onClose: (() -> Unit)?,
        vm: PlayerViewModel,
) {
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    // Deliberately NOT delegated with `by`: read only inside VinylScrubber, so the
    // 300ms position tick invalidates the scrubber, not this whole screen.
    val positionMs = vm.positionMs.collectAsStateWithLifecycle()
    val durationMs = vm.durationMs.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by vm.shuffleEnabled.collectAsStateWithLifecycle()
    val queueIndex by vm.queueIndex.collectAsStateWithLifecycle()
    val queueSize by remember { vm.queueItems.map { it.size } }.collectAsStateWithLifecycle(0)
    val songId by vm.currentSongId.collectAsStateWithLifecycle()
    val playbackError by vm.playbackError.collectAsStateWithLifecycle()
    val sonosActive by vm.sonosActive.collectAsStateWithLifecycle()
    val sonosVolume by vm.sonosVolume.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(playbackError) {
        playbackError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    // ── Track-change swipe ────────────────────────────────────────────────
    // The track only changes once per gesture, when the *accumulated* travel (or
    // the release velocity) clears the threshold — the original version tested
    // each pointer event's delta in isolation, so one continuous swipe fired a
    // skip on every frame that moved more than 28px.
    //
    // A committed swipe reads as a carousel: the current track keeps travelling
    // the way it was pushed until it has faded out, the new track is waited for,
    // then it enters from the opposite edge. Springing back to centre and
    // cross-dissolving in place (what this did before) never showed the outgoing
    // track leaving, which made the change read as a glitch rather than a move.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val heroOffset = remember { Animatable(0f) }
    // Deliberately not snapshot state: it is read when a content transition
    // starts, never to drive one, so observing it would only buy recompositions.
    val swapMode = remember { SwapMode() }
    val commitDistancePx = with(density) { SWIPE_COMMIT_DISTANCE.toPx() }
    val flickDistancePx = with(density) { SWIPE_FLICK_MIN_DISTANCE.toPx() }
    val flickVelocityPx = with(density) { SWIPE_FLICK_VELOCITY.toPx() }
    // Travel at which the card is fully transparent. Doubles as the drag clamp,
    // so a drag can reach the edge of visibility but never overshoot past it.
    val exitTravelPx = with(density) { SWIPE_EXIT_TRAVEL.toPx() }

    // ── Pull up for the queue ─────────────────────────────────────────────
    // Dragging up anywhere on the player (or on the handle at its foot) lifts the
    // screen a little and, past the threshold, opens the queue sheet. Sharing one
    // gesture detector with the track swipe (rather than stacking a second
    // pointerInput) is what keeps the two from both claiming a diagonal drag.
    val liftOffset = remember { Animatable(0f) }
    val openDistancePx = with(density) { PULL_OPEN_DISTANCE.toPx() }
    val liftMaxPx = with(density) { PULL_MAX_TRAVEL.toPx() }
    val pullFlickDistancePx = with(density) { PULL_FLICK_MIN_DISTANCE.toPx() }
    val pullFlickVelocityPx = with(density) { PULL_FLICK_VELOCITY.toPx() }
    val axisLockPx = with(density) { AXIS_LOCK_DISTANCE.toPx() }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false }, vm = vm)

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        // Ambient gradient — two soft radial glows behind everything.
        AmbientGlow(accent = colors.accent)

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .graphicsLayer {
                                    // Pull-up feedback: the screen follows the finger
                                    // at a third of its travel, as if held back.
                                    translationY = liftOffset.value / 3f
                                }
                                .pointerInput(Unit) {
                                    val velocity = VelocityTracker()
                                    // Travel is accumulated here rather than read back from
                                    // the Animatables: they are driven by launched coroutines
                                    // and can lag the last few events, which is exactly the
                                    // distance a borderline gesture hinges on.
                                    var travelledX = 0f
                                    var travelledY = 0f
                                    var axis = DragAxis.UNDECIDED
                                    detectDragGestures(
                                            onDragStart = {
                                                travelledX = 0f
                                                travelledY = 0f
                                                axis = DragAxis.UNDECIDED
                                                velocity.resetTracking()
                                                scope.launch { heroOffset.stop() }
                                                scope.launch { liftOffset.stop() }
                                            },
                                            onDragCancel = {
                                                axis = DragAxis.UNDECIDED
                                                scope.launch {
                                                    heroOffset.animateTo(0f, SWIPE_SPRING)
                                                }
                                                scope.launch {
                                                    liftOffset.animateTo(0f, SWIPE_SPRING)
                                                }
                                            },
                                            onDragEnd = {
                                                val v = velocity.calculateVelocity()
                                                when (axis) {
                                                    DragAxis.VERTICAL -> {
                                                        val open =
                                                                -travelledY >= openDistancePx ||
                                                                        (-v.y >=
                                                                                pullFlickVelocityPx &&
                                                                                -travelledY >=
                                                                                        pullFlickDistancePx)
                                                        if (open) showQueue = true
                                                        scope.launch {
                                                            liftOffset.animateTo(0f, SWIPE_SPRING)
                                                        }
                                                    }
                                                    else -> {
                                                        val travel = travelledX
                                                        val committed =
                                                                abs(travel) >= commitDistancePx ||
                                                                        (abs(v.x) >=
                                                                                flickVelocityPx &&
                                                                                abs(travel) >=
                                                                                        flickDistancePx)
                                                        scope.launch {
                                                            if (committed) {
                                                                commitSwipe(
                                                                        forward = travel < 0,
                                                                        heroOffset = heroOffset,
                                                                        exitTravelPx = exitTravelPx,
                                                                        currentSongId =
                                                                                vm.currentSongId,
                                                                        swapMode = swapMode,
                                                                        skip = {
                                                                            if (travel < 0)
                                                                                    vm.skipToNext()
                                                                            else
                                                                                    vm
                                                                                            .skipToPrevious()
                                                                        },
                                                                )
                                                            } else {
                                                                heroOffset.animateTo(
                                                                        0f,
                                                                        SWIPE_SPRING
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                axis = DragAxis.UNDECIDED
                                            },
                                    ) { change, drag ->
                                        change.consume()
                                        velocity.addPosition(change.uptimeMillis, change.position)
                                        travelledX += drag.x
                                        travelledY += drag.y

                                        // Lock to whichever axis pulls clear first, so a
                                        // slightly diagonal swipe doesn't do both at once.
                                        if (axis == DragAxis.UNDECIDED) {
                                            axis =
                                                    when {
                                                        abs(travelledX) >= axisLockPx &&
                                                                abs(travelledX) > abs(travelledY) ->
                                                                DragAxis.HORIZONTAL
                                                        -travelledY >= axisLockPx &&
                                                                -travelledY > abs(travelledX) ->
                                                                DragAxis.VERTICAL
                                                        else -> DragAxis.UNDECIDED
                                                    }
                                        }

                                        when (axis) {
                                            DragAxis.HORIZONTAL -> {
                                                // Clamped travel: the card only slides as far
                                                // as the point where it is fully faded out, so
                                                // a long drag can't build an absurd offset.
                                                travelledX =
                                                        travelledX.coerceIn(
                                                                -exitTravelPx,
                                                                exitTravelPx,
                                                        )
                                                scope.launch { heroOffset.snapTo(travelledX) }
                                            }
                                            DragAxis.VERTICAL -> {
                                                // Upwards only — the queue comes from below.
                                                travelledY = travelledY.coerceIn(-liftMaxPx, 0f)
                                                scope.launch { liftOffset.snapTo(travelledY) }
                                            }
                                            DragAxis.UNDECIDED -> Unit
                                        }
                                    }
                                },
        ) {
            // ── Top bar ──────────────────────────────────────────────
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                                HugeIcons.ChevronDown,
                                contentDescription = "Close",
                                tint = colors.text,
                        )
                    }
                } else {
                    // Keeps the side label centred.
                    Spacer(Modifier.size(48.dp))
                }
                MonoText(
                        text = sideLabel(queueIndex),
                        color = colors.textDim,
                        size = MonoSize.S95,
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                            HugeIcons.Playlist,
                            contentDescription = "Queue",
                            tint = colors.text,
                    )
                }
            }

            // ── Hero area ────────────────────────────────────────────
            BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
            ) {
            // Cover at 58% of the width, smaller when the height left between the
            // top bar and the transport (tab bar below) can't also hold the text.
            val artSize = minOf(maxWidth * 0.58f, maxHeight - HERO_TEXT_RESERVE).coerceAtLeast(96.dp)
            Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The kicker is a fixed label, so it stays put — only the card
                // below it (cover + title + artist + album) travels with the swipe.
                SectionKicker(
                        label = "Now Playing",
                        color = colors.accent,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))

                Column(
                        // Read in the draw phase only: the swipe drives the layer,
                        // never a recomposition of the hero.
                        modifier =
                                Modifier.graphicsLayer {
                                    val travel = heroOffset.value
                                    translationX = travel
                                    alpha = (1f - abs(travel) / exitTravelPx).coerceIn(0f, 1f)
                                },
                        horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                // Album artwork — the visual anchor of the hero, above the title.
                // Keyed on the artwork URI so a track change cross-dissolves the two
                // covers instead of snapping; identical URIs (same album) don't animate.
                AnimatedContent(
                        // Freebox streams carry their embedded cover as artworkData (no URI).
                        targetState =
                                song?.mediaMetadata?.let { it.artworkUri ?: it.artworkData },
                        // A swipe is already a transition: the card carries the
                        // change, so the content swaps instantly while it sits
                        // off-screen at alpha 0. Dissolving as well would replay
                        // the outgoing cover on top of the incoming one as the
                        // card slides back in. Skips from the transport buttons
                        // have no such carrier and keep the dissolve.
                        transitionSpec = {
                            if (swapMode.instant) INSTANT_SWAP
                            else
                                    (fadeIn(tween(170)) +
                                                    scaleIn(tween(170), initialScale = 0.94f))
                                            .togetherWith(fadeOut(tween(110)))
                        },
                        label = "artwork",
                        modifier = Modifier.size(artSize),
                ) { artwork ->
                    AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                    Modifier.fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.surface2),
                    )
                }

                Spacer(Modifier.height(26.dp))

                // The title — Manrope SemiBold, auto-fit 56sp → 28sp over max
                // two lines. (The trailing-period flourish belonged to the old
                // serif treatment and reads as a typo on a neutral sans.)
                AnimatedContent(
                        targetState = song?.mediaMetadata?.title?.toString() ?: "—",
                        transitionSpec = {
                            if (swapMode.instant) INSTANT_SWAP
                            else fadeIn(tween(160)).togetherWith(fadeOut(tween(100)))
                        },
                        label = "title",
                ) { title ->
                    VinylHeroTitle(title)
                }

                Spacer(Modifier.height(10.dp))

                // Artist — tap to open the tag editor
                AnimatedContent(
                        targetState = song?.mediaMetadata?.artist?.toString() ?: "—",
                        transitionSpec = {
                            if (swapMode.instant) INSTANT_SWAP
                            else fadeIn(tween(160)).togetherWith(fadeOut(tween(100)))
                        },
                        label = "artist",
                ) { artist ->
                    Text(
                            text = artist,
                            style = text.libraryRow19,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier =
                                    Modifier.clickable(
                                            // Freebox tracks can't be re-tagged. Test the URI, not
                                            // the mediaId: an indexed Freebox track has a numeric
                                            // (negative) id and only its URI says fbx://; a live
                                            // browser track has the fbx:// url as mediaId.
                                            enabled =
                                                    (songId ?: -1L) >= 0L &&
                                                            !com.source.player.data.freebox.FreeboxPaths.isFreeboxUrl(
                                                                    song?.localConfiguration?.uri?.toString()
                                                            ) &&
                                                            !com.source.player.data.freebox.FreeboxPaths.isFreeboxUrl(
                                                                    song?.mediaId
                                                            )
                                    ) {
                                        songId?.let {
                                            navController.navigate(Routes.tagEditor(it))
                                        }
                                    },
                    )
                }

                val album = song?.mediaMetadata?.albumTitle?.toString()
                if (!album.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = album,
                            style = text.metaSmall115,
                            color = colors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                    )
                }
                }
            }
            }

            // ── Scrubber ─────────────────────────────────────────────
            VinylScrubber(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = colors.accent,
                    hair = colors.hair,
                    textDim = colors.textDim,
                    onSeek = { vm.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 28.dp),
            )

            Spacer(Modifier.height(14.dp))

            // ── Controls ─────────────────────────────────────────────
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Toggle tints ease between dim and accent instead of snapping, so the
                // on/off state change reads as a deliberate transition.
                val shuffleTint by
                        animateColorAsState(
                                targetValue = if (shuffleEnabled) colors.accent else colors.textDim,
                                animationSpec = tween(220),
                                label = "shuffleTint",
                        )
                IconButton(onClick = { vm.setShuffleEnabled(!shuffleEnabled) }) {
                    Icon(
                            HugeIcons.Shuffle,
                            contentDescription = "Shuffle",
                            tint = shuffleTint,
                            modifier = Modifier.size(22.dp),
                    )
                }

                Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                            onClick = { vm.skipToPrevious() },
                            modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                                HugeIcons.Previous,
                                contentDescription = "Previous",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp),
                        )
                    }
                    val playInteraction = remember { MutableInteractionSource() }
                    val playPressed by playInteraction.collectIsPressedAsState()
                    // Kept as State (no `by`) — read inside graphicsLayer, so the press
                    // animation runs in the draw phase without recomposing the row.
                    val playScale =
                            animateFloatAsState(
                                    targetValue = if (playPressed) 0.92f else 1f,
                                    animationSpec =
                                            spring(
                                                    dampingRatio =
                                                            Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                            ),
                                    label = "playScale",
                            )
                    FilledIconButton(
                            onClick = { if (isPlaying) vm.pause() else vm.play() },
                            modifier =
                                    Modifier.size(76.dp).graphicsLayer {
                                        scaleX = playScale.value
                                        scaleY = playScale.value
                                    },
                            shape = CircleShape,
                            interactionSource = playInteraction,
                            colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                            containerColor = colors.accent
                                    ),
                    ) {
                        AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (fadeIn(tween(120)) +
                                                    scaleIn(tween(180), initialScale = 0.7f))
                                            .togetherWith(
                                                    fadeOut(tween(90)) +
                                                            scaleOut(
                                                                    tween(180),
                                                                    targetScale = 0.7f
                                                            )
                                            )
                                },
                                label = "playPause",
                        ) { playing ->
                            Icon(
                                    if (playing) HugeIcons.Pause else HugeIcons.Play,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = colors.bg,
                                    modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    IconButton(
                            onClick = { vm.skipToNext() },
                            modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                                HugeIcons.Next,
                                contentDescription = "Next",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp),
                        )
                    }
                }

                val repeatTint by
                        animateColorAsState(
                                targetValue =
                                        if (repeatMode != 0) colors.accent else colors.textDim,
                                animationSpec = tween(220),
                                label = "repeatTint",
                        )
                IconButton(onClick = { vm.cycleRepeatMode() }) {
                    AnimatedContent(
                            targetState = repeatMode,
                            transitionSpec = {
                                fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                            },
                            label = "repeatIcon",
                    ) { mode ->
                        Icon(
                                imageVector =
                                        when (mode) {
                                            2 -> HugeIcons.RepeatOne
                                            else -> HugeIcons.Repeat
                                        },
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (sonosActive) {
                Spacer(Modifier.height(8.dp))
                SonosVolumeSlider(
                        volume = sonosVolume,
                        onVolumeChange = { vm.setSonosVolume(it) },
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }

            // ── Queue handle ─────────────────────────────────────────
            QueueHandle(
                    position = queueIndex + 1,
                    size = queueSize,
                    onOpen = { showQueue = true },
            )
        }
    }
}

/** Grip at the foot of the player: tap it or pull it up to open the queue sheet. */
@Composable
private fun QueueHandle(position: Int, size: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onOpen)
                            .padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
                Modifier.size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.textMute.copy(alpha = 0.6f))
        )
        Spacer(Modifier.height(6.dp))
        MonoText(
                text = if (size > 0) "Queue \u00B7 ${position.coerceIn(1, size)} / $size" else "Queue",
                color = colors.textDim,
                size = MonoSize.S10,
        )
    }
}

/** Nothing loaded: shuffle the active source or go pick something in Folders. */
@Composable
private fun PlayerEmptyState(
        canShuffle: Boolean,
        onShuffleAll: () -> Unit,
        onBrowseFolders: () -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        AmbientGlow(accent = colors.accent)
        Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrowserEmptyState(
                    icon = HugeIcons.MusicNote,
                    title = "Nothing playing",
                    body =
                            if (canShuffle) "Shuffle the whole library, or pick something in Folders."
                            else "Pick something to play in Folders.",
                    action = if (canShuffle) "Shuffle all" else null,
                    onAction = onShuffleAll,
            )
            TextButton(onClick = onBrowseFolders) {
                Text("Browse folders", style = text.pillLabel12, color = colors.accent)
            }
        }
    }
}

// ── Ambient background ────────────────────────────────────────────────────
@Composable
private fun AmbientGlow(accent: Color) {
    // Two soft radial gradients — one accent-tinted top-left, one plum bottom-right.
    val plum = remember { oklchToColor(0.35f, 0.08f, 300f) }
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.radialGradient(
                                            colors =
                                                    listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                                            radius = 900f,
                                    )
                            ),
    )
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.radialGradient(
                                            colors =
                                                    listOf(plum.copy(alpha = 0.12f), Color.Transparent),
                                            radius = 1100f,
                                    )
                            ),
    )
}

// ── Hero title with measured auto-fit ─────────────────────────────────────
// Manrope SemiBold, auto-fit: largest 4sp step in [28, 56] whose rendering
// fits two lines. Binary-searched with a TextMeasurer in a single composition
// (an onTextLayout shrink loop would relayout once per candidate size).
@Composable
private fun VinylHeroTitle(title: String) {
    val base = MaterialTheme.sourceText.playerTitle56
    val colors = MaterialTheme.sourceColors
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth

        fun styleFor(size: Float) = base.copy(fontSize = size.sp)

        val titleSize =
                remember(title, maxWidthPx) {
                    fun fits(size: Float): Boolean {
                        val result =
                                measurer.measure(
                                        text = AnnotatedString(title),
                                        style = styleFor(size),
                                        constraints = Constraints(maxWidth = maxWidthPx),
                                        maxLines = 2,
                                        softWrap = true,
                                )
                        return !result.didOverflowHeight && !result.didOverflowWidth
                    }
                    // Largest size in {56, 52, …, 28} that fits; 28 is the floor
                    // even when it still overflows.
                    var best = 28f
                    var lo = 0
                    var hi = 7
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        val candidate = 56f - 4f * mid
                        if (fits(candidate)) {
                            best = candidate
                            hi = mid - 1
                        } else {
                            lo = mid + 1
                        }
                    }
                    best
                }

        Text(
                text = title,
                style = styleFor(titleSize),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Visible,
        )
    }
}

// ── Scrubber ──────────────────────────────────────────────────────────────
// Takes State<Long> instead of raw values: the 300ms position tick then
// recomposes only this small leaf, never the whole PlayerScreen.
@Composable
private fun VinylScrubber(
        positionMs: State<Long>,
        durationMs: State<Long>,
        accent: Color,
        hair: Color,
        textDim: Color,
        onSeek: (Long) -> Unit,
        modifier: Modifier = Modifier,
) {
    val position = positionMs.value
    val duration = durationMs.value
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val display = if (isDragging) dragProgress else progress
    val elapsed = if (isDragging) (dragProgress * duration).toLong() else position
    val remainingMs = (duration - elapsed).coerceAtLeast(0L)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                                        // durationMs.value read at event time — no need to
                                        // restart the gesture detector when it changes.
                                        onSeek((f * durationMs.value).toLong())
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragProgress =
                                                        (offset.x / size.width).coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                onSeek(
                                                        (dragProgress * durationMs.value)
                                                                .toLong()
                                                )
                                                isDragging = false
                                            },
                                            onDragCancel = { isDragging = false },
                                            onHorizontalDrag = { _, d ->
                                                dragProgress =
                                                        (dragProgress + d / size.width)
                                                                .coerceIn(0f, 1f)
                                            },
                                    )
                                },
                contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                val trackY = size.height / 2
                val stroke = 2.dp.toPx()
                drawLine(
                        color = hair,
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = stroke,
                )
                val x = size.width * display
                if (x > 0f) {
                    drawLine(
                            color = accent,
                            start = Offset(0f, trackY),
                            end = Offset(x, trackY),
                            strokeWidth = stroke,
                    )
                }
                drawCircle(color = accent, radius = 5.dp.toPx(), center = Offset(x, trackY))
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoText(text = formatDuration(elapsed), color = textDim, size = MonoSize.S105)
            MonoText(
                    text = "-" + formatDuration(remainingMs),
                    color = textDim,
                    size = MonoSize.S105,
            )
        }
    }
}

// ── Sonos volume slider (scoped to Player) ────────────────────────────────
@Composable
private fun SonosVolumeSlider(
        volume: Int?,
        onVolumeChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val display = dragValue ?: (volume?.toFloat() ?: 0f)
    val colors = MaterialTheme.sourceColors
    Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HugeIcons.VolumeLow, null, tint = colors.textDim, modifier = Modifier.size(18.dp))
        Slider(
                value = display,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onVolumeChange(it.toInt()) }
                    dragValue = null
                },
                valueRange = 0f..100f,
                colors =
                        SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.hair,
                        ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(HugeIcons.VolumeHigh, null, tint = colors.textDim, modifier = Modifier.size(18.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────
/** "SIDE A · 04" style label derived from queue index (1-based). */
private fun sideLabel(index: Int): String {
    val side = if (index < 12) "A" else if (index < 24) "B" else "C"
    return "SIDE $side \u00B7 %02d".format((index + 1).coerceAtLeast(1))
}

fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val min = totalSecs / 60
    val sec = totalSecs % 60
    return "%d:%02d".format(min, sec)
}

/**
 * Plays out a committed swipe as a single continuous move.
 *
 * 1. The card finishes travelling the way it was pushed, fading to nothing.
 * 2. The skip is issued.
 * 3. We wait for the new track to actually reach the UI — playback state
 *    arrives a few frames after the call, and entering before it lands would
 *    slide the *outgoing* track back in, which is what made the transition read
 *    as a glitch. Bounded by a timeout so a skip the player refuses (end of a
 *    non-repeating queue) still restores the card instead of leaving it blank.
 * 4. The card jumps to the far edge — invisible at alpha 0 — and springs back in.
 */
private suspend fun commitSwipe(
        forward: Boolean,
        heroOffset: Animatable<Float, *>,
        exitTravelPx: Float,
        currentSongId: StateFlow<Long?>,
        swapMode: SwapMode,
        skip: () -> Unit,
) {
    val exitTarget = if (forward) -exitTravelPx else exitTravelPx
    val previousId = currentSongId.value

    swapMode.instant = true
    try {
        heroOffset.animateTo(exitTarget, SWIPE_EXIT_SPEC)
        skip()
        withTimeoutOrNull(SKIP_SETTLE_TIMEOUT_MS) { currentSongId.first { it != previousId } }
        // The flow emitting is not the same thing as the card showing the new
        // track: composition still has to run. Waiting two frames is what stops
        // the entry from replaying the outgoing track for a frame.
        withFrameNanos {}
        withFrameNanos {}
        heroOffset.snapTo(-exitTarget)
        heroOffset.animateTo(0f, SWIPE_ENTER_SPEC)
    } finally {
        swapMode.instant = false
    }
}

/** Whether the next hero content change is carried by the swipe (see above). */
private class SwapMode {
    var instant = false
}

/** Which way a drag on the player was resolved to. */
private enum class DragAxis {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL,
}

// ── Swipe tuning ──────────────────────────────────────────────────────────
// A gesture commits at most one track change, and only when it clears one of
// two bars: a deliberate drag past SWIPE_COMMIT_DISTANCE, or a quick flick
// (SWIPE_FLICK_VELOCITY) that still travelled SWIPE_FLICK_MIN_DISTANCE. The
// distances are in dp so the feel is identical across screen densities.
private val SWIPE_COMMIT_DISTANCE = 80.dp
private val SWIPE_FLICK_MIN_DISTANCE = 28.dp
private val SWIPE_FLICK_VELOCITY = 450.dp // per second
private val SWIPE_EXIT_TRAVEL = 190.dp

// ── Pull-up tuning ────────────────────────────────────────────────────────
// The distance a drag must clear before it is treated as one axis rather than
// the other, then the pull-up's own open threshold and travel clamp.
private val AXIS_LOCK_DISTANCE = 10.dp
private val PULL_OPEN_DISTANCE = 90.dp
private val PULL_MAX_TRAVEL = 240.dp

// The pull-up deliberately does NOT reuse the track swipe's flick bar: a flick
// has to be both longer and faster before it counts, so a brisk thumb movement
// on the cover doesn't throw the queue over the player by accident.
private val PULL_FLICK_MIN_DISTANCE = 56.dp
private val PULL_FLICK_VELOCITY = 900.dp // per second

/**
 * Height the hero keeps for what sits under the cover: kicker, 2-line auto-fit title,
 * artist, album and their spacing. The cover shrinks rather than push them out.
 */
private val HERO_TEXT_RESERVE = 250.dp

/** Spring back to centre when a gesture didn't clear the threshold. */
private val SWIPE_SPRING =
        spring<Float>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
        )

/** Accelerating exit — the card is being thrown off, not eased off. */
private val SWIPE_EXIT_SPEC = tween<Float>(durationMillis = 140, easing = FastOutLinearInEasing)

/** Entry settles rather than bounces: a bouncy arrival reads as indecision. */
private val SWIPE_ENTER_SPEC =
        spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
        )

/** Upper bound on waiting for the player to report the new track. */
private const val SKIP_SETTLE_TIMEOUT_MS = 400L

/** Hard cut, used while the hero card is off-screen at alpha 0. */
private val INSTANT_SWAP = fadeIn(snap()).togetherWith(fadeOut(snap()))
