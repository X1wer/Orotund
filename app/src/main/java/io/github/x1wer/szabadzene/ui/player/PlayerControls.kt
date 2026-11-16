package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.FilledIconButton
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.RepeatSetting
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.playback.SleepTimerState
import io.github.x1wer.szabadzene.ui.components.CompactSkipButton
import io.github.x1wer.szabadzene.ui.components.SkipButton
import io.github.x1wer.szabadzene.ui.components.SkipButtonMinSize
import io.github.x1wer.szabadzene.ui.components.SkipButtonTags
import io.github.x1wer.szabadzene.ui.formatDuration
import io.github.x1wer.szabadzene.ui.sound.rememberSleepRemaining
import io.github.x1wer.szabadzene.ui.sound.sleepTimerSummary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * A lejátszófelületek műveletei. A teljes és a minilejátszó ugyanezt használja.
 * A null értékű műveletek gombja nem jelenik meg.
 */
data class PlayerCallbacks(
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    /** A fő (beállított) ugrás az adott irányba. */
    val onSkip: (SkipDirection) -> Unit = {},
    /** Ugrás megadott másodperccel (a teljes lejátszó további ugrógombjai); ugyanaz a közös ugrási művelet. */
    val onSkipBy: (SkipDirection, Int) -> Unit = { _, _ -> },
    val onToggleShuffle: () -> Unit = {},
    /** Az ismétlés gombja: megnyitja a módválasztót. */
    val onOpenRepeat: () -> Unit = {},
    val onToggleFavorite: (() -> Unit)? = null,
    val onOpenQueue: (() -> Unit)? = null,
    val onOpenEqualizer: (() -> Unit)? = null,
    val onOpenSpeed: (() -> Unit)? = null,
    val onOpenVolume: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onOpenSleepTimer: (() -> Unit)? = null,
    val onOpenSkipSettings: (() -> Unit)? = null,
)

object PlayerTags {
    const val SEEK_BAR = "seek_bar"
    const val PLAY_PAUSE = "play_pause"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val SHUFFLE = "shuffle"
    const val REPEAT = "repeat"
    const val FAVORITE = "favorite"
    const val QUEUE = "queue"
    const val EQUALIZER = "equalizer"
    const val SPEED = "speed"
    const val VOLUME = "volume"
    const val ADD_TO_PLAYLIST = "add_to_playlist"
    const val SLEEP_TIMER = "sleep_timer"
    const val SKIP_ROW = "skip_row"
    const val SKIP_SETTINGS = "skip_settings"
    const val NEXT_UP = "next_up"
}

/** Az ugrógombok közötti térköz a teljes lejátszó tömör sorában (a gombok maguk legalább 56 dp-sek). */
private val SkipGap = 2.dp

/** A beállítás-fogaskerék szélessége az ugrósor végén. */
private val GearWidth = 48.dp

/**
 * Időcsúszka. Húzás közben a felhasználó által választott pozíciót mutatja; a háttérből érkező
 * állapotfrissítés ilyenkor nem rántja vissza.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var settleTarget by remember { mutableStateOf<Long?>(null) }

    // Elengedés után a cél látszik, amíg a lejátszó új pozíciót nem jelent (legfeljebb 1 mp-ig).
    LaunchedEffect(positionMs) { settleTarget = null }
    LaunchedEffect(settleTarget) {
        if (settleTarget != null) {
            delay(1_000)
            settleTarget = null
        }
    }

    val canSeek = enabled && durationMs > 0
    val shownMs = when {
        !canSeek -> positionMs.coerceAtLeast(0L)
        dragFraction != null -> (dragFraction!! * durationMs).toLong()
        settleTarget != null -> settleTarget!!
        else -> positionMs
    }.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val fraction = if (durationMs > 0) shownMs.toFloat() / durationMs else 0f
    val description = stringResource(R.string.cd_seek_bar)

    Column(modifier) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val f = dragFraction
                if (f != null) {
                    val target = (f * durationMs).toLong()
                    settleTarget = target
                    dragFraction = null
                    onSeek(target)
                }
            },
            enabled = canSeek,
            modifier = Modifier
                .testTag(PlayerTags.SEEK_BAR)
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(shownMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0) formatDuration(durationMs) else "–:––",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A teljes lejátszó állandóan látható ugrógombjai, közvetlenül az időcsúszka alatt.
 *
 * Ha elfér, irányonként több gomb (a fő érték és a „további ugrógombok”, pl. −30 −10 −5 | +5 +10 +30),
 * mindegyik legalább 56 × 56 dp-s. Ha csak egy pár fér el, a fő értékű pár marad, nagy, feliratos
 * gombként. A fő értékű gomb sosem tűnik el, és semmi nem kerül menübe.
 */
@Composable
fun SkipRow(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .testTag(PlayerTags.SKIP_ROW)
            .fillMaxWidth(),
    ) {
        val gear = callbacks.onOpenSkipSettings
        val backSteps = skip.stepsFor(back = true)
        val forwardSteps = skip.stepsFor(back = false)
        val maxPerSide = maxOf(backSteps.size, forwardSteps.size)
        fun fit(availableDp: Float) = SkipSettings.buttonsPerSide(maxPerSide, availableDp, SkipButtonMinSize.value, SkipGap.value)
        // Inkább a fogaskerék marad el (a beállítás a Beállításokban is elérhető), mint egy ugrógomb.
        val withGear = if (gear != null) fit(maxWidth.value - (GearWidth + SkipGap).value) else 0
        val withoutGear = fit(maxWidth.value)
        val showGear = gear != null && withGear >= withoutGear
        val perSide = if (showGear) withGear else withoutGear
        if (perSide >= 2) {
            val back = SkipSettings.visibleSteps(backSteps, skip.backSeconds, perSide).sortedDescending()
            val forward = SkipSettings.visibleSteps(forwardSteps, skip.forwardSeconds, perSide)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SkipGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                back.forEach { seconds ->
                    CompactSkipFor(SkipDirection.BACK, seconds, skip.backSeconds, state, callbacks)
                }
                forward.forEach { seconds ->
                    CompactSkipFor(SkipDirection.FORWARD, seconds, skip.forwardSeconds, state, callbacks)
                }
                if (showGear && gear != null) SkipSettingsButton(gear)
            }
        } else {
            val showIcon = LocalDensity.current.fontScale < 1.5f
            // A gomb csak akkor fér el a két nagy gomb mellett, ha marad hely.
            val gearFits = gear != null && maxWidth >= 112.dp * 2 + 12.dp * 2 + GearWidth
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(
                    direction = SkipDirection.BACK,
                    seconds = skip.backSeconds,
                    enabled = state.canSeek,
                    onClick = { callbacks.onSkip(SkipDirection.BACK) },
                    showIcon = showIcon,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SkipButton(
                    direction = SkipDirection.FORWARD,
                    seconds = skip.forwardSeconds,
                    enabled = state.canSeek,
                    onClick = { callbacks.onSkip(SkipDirection.FORWARD) },
                    showIcon = showIcon,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (gearFits && gear != null) SkipSettingsButton(gear)
            }
        }
    }
}

@Composable
private fun CompactSkipFor(
    direction: SkipDirection,
    seconds: Int,
    mainSeconds: Int,
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
) {
    val isMain = seconds == mainSeconds
    CompactSkipButton(
        direction = direction,
        seconds = seconds,
        enabled = state.canSeek,
        onClick = { if (isMain) callbacks.onSkip(direction) else callbacks.onSkipBy(direction, seconds) },
        tag = if (isMain) SkipButtonTags.forDirection(direction) else SkipButtonTags.extra(direction, seconds),
    )
}

@Composable
private fun SkipSettingsButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(PlayerTags.SKIP_SETTINGS)
            .size(width = GearWidth, height = SkipButtonMinSize),
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_skip_settings))
    }
}

/**
 * Vezérlősor: véletlen sorrend, előző szám, nagy lejátszás/szünet, következő szám, ismétlés.
 * Az előző/következő szám ikonja eltér az ugrógombokétól, és külön sorban van.
 */
@Composable
fun TransportRow(
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
    playSize: Int = 72,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.7f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = callbacks.onToggleShuffle,
            enabled = state.hasMedia,
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (state.shuffle) active else inactive),
            modifier = Modifier
                .testTag(PlayerTags.SHUFFLE)
                .size(48.dp),
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(if (state.shuffle) R.string.cd_shuffle_on else R.string.cd_shuffle_off),
            )
        }
        PreviousButton(state, callbacks.onPrevious, size = 56.dp)
        PlayPauseButton(
            isPlaying = state.playWhenReady,
            enabled = state.hasMedia,
            onClick = callbacks.onPlayPause,
            size = playSize,
        )
        NextButton(state, callbacks.onNext, size = 56.dp)
        RepeatButton(state, callbacks.onOpenRepeat)
    }
}

@Composable
fun PreviousButton(state: PlaybackUiState, onClick: () -> Unit, size: Dp) {
    IconButton(
        onClick = onClick,
        enabled = state.hasPrevious,
        modifier = Modifier
            .testTag(PlayerTags.PREVIOUS)
            .size(size),
    ) {
        Icon(
            Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous),
            size = size * 0.62f,
        )
    }
}

@Composable
fun NextButton(state: PlaybackUiState, onClick: () -> Unit, size: Dp) {
    IconButton(
        onClick = onClick,
        enabled = state.hasNext,
        modifier = Modifier
            .testTag(PlayerTags.NEXT)
            .size(size),
    ) {
        Icon(
            Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
            size = size * 0.62f,
        )
    }
}

/** Az ismétlési mód ikonja (a módválasztó ablakban is ugyanez). */
fun repeatIcon(mode: RepeatSetting): ImageVector = when (mode) {
    RepeatSetting.STOP_AFTER_CURRENT -> Icons.Rounded.StopCircle
    RepeatSetting.ONE -> Icons.Rounded.RepeatOne
    RepeatSetting.OFF -> Icons.AutoMirrored.Rounded.PlaylistPlay
    RepeatSetting.ALL -> Icons.Rounded.Repeat
}

fun repeatLabel(mode: RepeatSetting): Int = when (mode) {
    RepeatSetting.STOP_AFTER_CURRENT -> R.string.repeat_mode_stop
    RepeatSetting.ONE -> R.string.repeat_mode_one
    RepeatSetting.OFF -> R.string.repeat_mode_off
    RepeatSetting.ALL -> R.string.repeat_mode_all
}

@Composable
private fun RepeatButton(state: PlaybackUiState, onClick: () -> Unit) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.7f)
    val description = stringResource(R.string.cd_repeat_mode, stringResource(repeatLabel(state.repeat)))
    IconButton(
        onClick = onClick,
        enabled = state.hasMedia,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (state.repeat == RepeatSetting.OFF) inactive else active,
        ),
        modifier = Modifier
            .testTag(PlayerTags.REPEAT)
            .size(48.dp),
    ) {
        Icon(repeatIcon(state.repeat), contentDescription = description)
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .testTag(PlayerTags.PLAY_PAUSE)
            .size(size.dp),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
            size = (size * 0.5f).dp,
        )
    }
}

/** A lejátszási sebesség kijelzése („1.25×”), mindig ponttal, hogy rövid maradjon. */
fun speedText(speed: Float): String {
    val text = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').let { if (it.endsWith('.')) it + "0" else it }
    return "$text×"
}

/**
 * Műveletsor a cím alatt: sebesség, hangszínszabályzó, kedvenc, listához adás, elalvásidőzítő,
 * hangerő. Keskeny helyen két sorba törik; a gombok nem kerülnek menübe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    speed: Float = 1f,
    sleepTimer: SleepTimerState = SleepTimerState.Off,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.75f)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        callbacks.onOpenSpeed?.let { open ->
            val description = stringResource(R.string.cd_speed, speedText(speed))
            Column(
                Modifier
                    .testTag(PlayerTags.SPEED)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = open)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (speed != 1f) active else inactive
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = color, size = 22.dp)
                Text(speedText(speed), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
        callbacks.onOpenEqualizer?.let { open ->
            ActionIcon(Icons.Rounded.Equalizer, stringResource(R.string.cd_equalizer), PlayerTags.EQUALIZER, inactive, open)
        }
        callbacks.onToggleFavorite?.let { toggle ->
            ActionIcon(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                description = stringResource(if (isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off),
                tag = PlayerTags.FAVORITE,
                tint = if (isFavorite) active else inactive,
                onClick = toggle,
                enabled = state.hasMedia,
            )
        }
        callbacks.onAddToPlaylist?.let { add ->
            ActionIcon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                stringResource(R.string.action_add_to_playlist),
                PlayerTags.ADD_TO_PLAYLIST,
                inactive,
                add,
                enabled = state.hasMedia,
            )
        }
        callbacks.onOpenSleepTimer?.let { open -> SleepTimerButton(sleepTimer, open) }
        callbacks.onOpenVolume?.let { open ->
            ActionIcon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.cd_volume), PlayerTags.VOLUME, inactive, open)
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    description: String,
    tag: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = Modifier
            .testTag(tag)
            .size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Az elalvásidőzítő gombja; ha fut, mellette a hátralévő idő (vagy „Szám vége”). */
@Composable
private fun SleepTimerButton(state: SleepTimerState, onClick: () -> Unit) {
    val remaining = rememberSleepRemaining(state)
    val active = state != SleepTimerState.Off
    val label = when (state) {
        SleepTimerState.Off -> null
        SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_badge_end)
        is SleepTimerState.Timed -> remaining
    }
    val description = if (active) {
        stringResource(R.string.cd_sleep_timer_on, sleepTimerSummary(state))
    } else {
        stringResource(R.string.cd_sleep_timer_off)
    }
    val color = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.75f)
    Row(
        Modifier
            .testTag(PlayerTags.SLEEP_TIMER)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = color)
        if (label != null) {
            Spacer(Modifier.size(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** A várólista gombja a sorszámmal („#22/48”); a várólistát nyitja meg. */
@Composable
fun QueuePositionButton(state: PlaybackUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (state.queuePosition > 0) {
        stringResource(R.string.cd_queue_position, state.queuePosition, state.queueSize)
    } else {
        stringResource(R.string.cd_open_queue)
    }
    Column(
        modifier
            .testTag(PlayerTags.QUEUE)
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, enabled = state.hasMedia, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = LocalContentColor.current.copy(alpha = 0.8f)
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = color, size = 22.dp)
        if (state.queuePosition > 0) {
            // Rögzített betűméret: a gomb nagy betűnél sem nő túl.
            val density = LocalDensity.current
            val size = with(density) { 12.dp.toSp() }
            Text(
                text = "#${state.queuePosition}/${state.queueSize}",
                fontSize = size,
                lineHeight = size,
                color = color,
                maxLines = 1,
            )
        }
    }
}
