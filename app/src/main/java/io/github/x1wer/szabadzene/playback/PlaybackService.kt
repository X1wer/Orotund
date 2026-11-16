package io.github.x1wer.szabadzene.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.MainActivity
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.SzabadZeneApp
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.settings.HeadsetSettings
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A lejátszó és a MediaSession egyetlen otthona. A felület MediaControlleren keresztül
 * kapcsolódik, így forgatás vagy képernyőváltás nem hoz létre új lejátszót.
 *
 * A várólistát, a pozíciót és a módokat menti; induláskor visszaállítja őket, de nem indít
 * lejátszást (kivéve, ha a rendszer kifejezetten folytatást kér, pl. Bluetooth-lejátszógomb).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var container: AppContainer
    private lateinit var queueStore: QueueStore
    private val scope = MainScope()
    private val skipController = SkipController(clock = SystemClock::elapsedRealtime)
    private var skipSettings = SkipSettings()
    private var headsetSettings = HeadsetSettings()
    private var playbackPrefs = PlaybackPrefs()

    /** Lejátszásszámláló: a beszámított lejátszás az alkalmazás hatókörében íródik az adatbázisba. */
    private val playCounter = PlayCountTracker(clock = SystemClock::elapsedRealtime) { mediaId ->
        val id = mediaId.toLongOrNull() ?: return@PlayCountTracker
        container.appScope.launch(Dispatchers.IO) { runCatching { container.playStats.recordPlay(id) } }
    }
    private var playCountJob: Job? = null

    /** Egymást követő lejátszási hibák száma; véd a végtelen továbbugrás ellen. */
    private var consecutiveErrors = 0

    /** Mentés csak a visszaállítás után: különben az induló üres lista felülírná a mentettet. */
    private var restoreDone = false
    private var saveJob: Job? = null
    private var periodicSaveJob: Job? = null

    /** Fejhallgató-kihúzás vagy Bluetooth-megszakadás miatt állt-e meg (az automatikus folytatáshoz). */
    private var pausedByDisconnect = false
    private var pausedByDisconnectAt = 0L
    private var audioManager: AudioManager? = null

    private lateinit var effects: AudioEffectsController
    private val sleepTimer = SleepTimer(clock = SystemClock::elapsedRealtime)
    private var sleepJob: Job? = null
    private var widgetJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as SzabadZeneApp).container
        queueStore = QueueStore(container.database.queueDao())

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Fejhallgató kihúzásakor vagy Bluetooth-kimenet megszakadásakor szünet (beállítható).
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents())
        isRunning = true
        current = java.lang.ref.WeakReference(this)

        // Hangszínszabályzó: a lejátszó hangmunkamenetére kötve, a beállítások szerint.
        effects = AudioEffectsController(container.audioEffects)
        effects.attach(player.audioSessionId)
        scope.launch { container.settings.equalizer.collect { effects.apply(it) } }

        val sessionPlayer = SkipAwarePlayer(
            player = player,
            onSkip = { direction -> performSkip(direction, stepMs = null) },
            stepMs = { direction -> stepFor(direction) },
            onItemsInserted = ::onItemsInserted,
            restartThresholdMs = { playbackPrefs.restartThresholdMs },
        )

        session = MediaSession.Builder(this, sessionPlayer)
            .setCallback(SessionCallback())
            .setSessionActivity(sessionActivity())
            .setMediaButtonPreferences(mediaButtons())
            .build()

        scope.launch {
            container.settings.skipSettings.collect { settings ->
                skipSettings = settings
                session?.setMediaButtonPreferences(mediaButtons())
                updateWidget()
            }
        }
        scope.launch {
            container.settings.playback.collect { prefs ->
                val stopModeChanged = prefs.stopAfterCurrent != playbackPrefs.stopAfterCurrent
                playbackPrefs = prefs
                if (player.playbackParameters.speed != prefs.speed) player.setPlaybackSpeed(prefs.speed)
                if (stopModeChanged) updatePauseAtEnd()
            }
        }
        scope.launch {
            container.settings.headset.collect { settings ->
                headsetSettings = settings
                player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
            }
        }
        audioManager = getSystemService(AudioManager::class.java)?.also {
            it.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        }
        scope.launch { restoreQueue() }
        updateWidget()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveInBackground()
        // Ha a felhasználó bezárja az appot, és nem szól zene, a szolgáltatás is álljon le.
        val current = session?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0 ||
            current.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveInBackground()
        isRunning = false
        current = null
        sleepJob?.cancel()
        playCountJob?.cancel()
        effects.release()
        PlayerWidget.markStopped(applicationContext)
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // --- Ugrás ---

    private fun stepFor(direction: SkipDirection): Long = when (direction) {
        SkipDirection.BACK -> skipSettings.backMs
        SkipDirection.FORWARD -> skipSettings.forwardMs
    }

    private fun performSkip(direction: SkipDirection, stepMs: Long?) {
        val step = stepMs?.takeIf { it > 0 } ?: stepFor(direction)
        skipController.skip(PlayerSkipTarget(player), direction, step)
    }

    // --- Elalvásidőzítő ---

    private fun setSleepTimer(durationMs: Long?, endOfTrack: Boolean) {
        when {
            endOfTrack -> sleepTimer.startEndOfTrack()
            durationMs != null && durationMs > 0 -> sleepTimer.startTimed(durationMs)
            else -> sleepTimer.cancel()
        }
        onSleepTimerChanged()
    }

    /** A szám végén szünet: az időzítő „szám végén” módja vagy a „leállás az aktuális szám végén” ismétlési mód. */
    private fun updatePauseAtEnd() {
        player.pauseAtEndOfMediaItems = sleepTimer.pauseAtEndOfItems || playbackPrefs.stopAfterCurrent
    }

    private fun onSleepTimerChanged() {
        updatePauseAtEnd()
        sleepJob?.cancel()
        sleepJob = (sleepTimer.state as? SleepTimerState.Timed)?.let {
            scope.launch {
                while (isActive) {
                    val remaining = sleepTimer.remainingMs() ?: break
                    delay(remaining.coerceIn(50L, 1_000L))
                    if (sleepTimer.onTick() == SleepTimerAction.PAUSE_NOW) {
                        player.pause()
                        onSleepTimerChanged()
                        break
                    }
                }
            }
        }
        session?.setSessionExtras(sleepTimer.state.toBundle())
    }

    // --- Fájlmódosítás után ---

    /**
     * A megadott számok újratöltése (a fájl tartalma megváltozott, pl. címkeírás után). Az elemek új
     * gyorsítótár-kulcsot kapnak, így a lejátszó újra megnyitja a fájlt; a sorrend, a kevert
     * sorrend, a pozíció és a lejátszás/szünet állapot változatlan marad.
     */
    private suspend fun reloadTracks(ids: Set<Long>) {
        if (ids.isEmpty() || player.mediaItemCount == 0) return
        val fresh = container.library.tracksByIds(ids)
        val sequence = currentShuffleSequence()
        val currentIndex = player.currentMediaItemIndex
        val position = player.currentPosition
        var currentReplaced = false
        val stamp = SystemClock.elapsedRealtime()
        for (index in 0 until player.mediaItemCount) {
            val id = player.getMediaItemAt(index).mediaId.toLongOrNull() ?: continue
            if (id !in ids) continue
            val track = fresh[id] ?: continue
            val item = track.toMediaItem().buildUpon().setCustomCacheKey("$id@$stamp").build()
            player.replaceMediaItem(index, item)
            if (index == currentIndex) currentReplaced = true
        }
        applyShuffleSequence(sequence)
        if (currentReplaced) player.seekTo(currentIndex, position)
        updateWidget()
    }

    // --- Lejátszás/Keverés „a mostani szám végigszól” módban ---

    /**
     * A várólista a megadott számokra cserélődik, de a most szóló szám a helyén marad és zavartalanul
     * szól tovább; utána sorrendben a lista következő száma, keverésnél egy véletlen szám jön. Közvetlenül
     * az ExoPlayert módosítja (a beszúrás-figyelő itt nem kell, a kevert sorrendet maga állítja be).
     */
    private suspend fun playKeepingCurrent(ids: List<Long>, shuffle: Boolean) {
        val current = player.currentMediaItem ?: return
        if (ids.isEmpty()) return
        val tracks = container.library.tracksByIds(ids)
        val plan = QueueLogic.keepCurrentPlan(ids.filter { it in tracks }, current.mediaId.toLongOrNull(), shuffle, kotlin.random.Random.Default)
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex + 1 < player.mediaItemCount) player.removeMediaItems(currentIndex + 1, player.mediaItemCount)
        if (currentIndex > 0) player.removeMediaItems(0, currentIndex)
        player.shuffleModeEnabled = shuffle
        player.addMediaItems(0, plan.before.map { tracks.getValue(it).toMediaItem() })
        player.addMediaItems(plan.after.map { tracks.getValue(it).toMediaItem() })
        plan.shuffleOrder?.let { applyShuffleSequence(it) }
        scheduleSave()
        updateWidget()
    }

    // --- Widget ---

    private fun updateWidget() {
        val item = player.currentMediaItem
        val snapshot = PlayerWidget.Snapshot(
            trackId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED && item != null,
            nextTitle = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET && it < player.mediaItemCount }
                ?.let { player.getMediaItemAt(it).mediaMetadata.title?.toString() },
            backSeconds = skipSettings.backSeconds,
            forwardSeconds = skipSettings.forwardSeconds,
        )
        widgetJob?.cancel()
        widgetJob = scope.launch {
            val art = snapshot.trackId?.let { id ->
                runCatching { container.artwork.loadBitmap(id, PlayerWidget.ART_SIZE_PX) }.getOrNull()
            }
            PlayerWidget.update(applicationContext, snapshot, art)
        }
    }

    // --- Kevert sorrend ---

    /** Az aktuális kevert sorrend az alaplista indexeivel. */
    private fun currentShuffleSequence(): List<Int> {
        val order = player.shuffleOrder
        if (order.length != player.mediaItemCount) return (0 until player.mediaItemCount).toList()
        val result = ArrayList<Int>(order.length)
        var index = order.firstIndex
        while (index != C.INDEX_UNSET && result.size < order.length) {
            result += index
            index = order.getNextIndex(index)
        }
        return result
    }

    private fun applyShuffleSequence(sequence: List<Int>) {
        if (sequence.size != player.mediaItemCount) return
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(sequence.toIntArray(), SystemClock.elapsedRealtime()))
    }

    /**
     * Keverés közben a „Lejátszás következőként” elemek tényleg a következők legyenek, a
     * „Hozzáadás a várólistához” elemek pedig a sor végére kerüljenek.
     */
    private fun onItemsInserted(index: Int, count: Int, currentIndexBefore: Int) {
        if (!player.shuffleModeEnabled || count <= 0) return
        val inserted = (index until index + count).toList()
        val sequence = currentShuffleSequence()
        val newSequence = when {
            index == currentIndexBefore + 1 -> QueueLogic.playNextOrder(sequence, player.currentMediaItemIndex, inserted)
            index + count == player.mediaItemCount -> QueueLogic.appendOrder(sequence, inserted)
            else -> return
        }
        applyShuffleSequence(newSequence)
    }

    // --- Mentés és visszaállítás ---

    private fun snapshot(): SavedQueue = SavedQueue(
        trackIds = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() },
        currentIndex = player.currentMediaItemIndex,
        positionMs = player.currentPosition,
        repeatMode = player.repeatMode,
        shuffleEnabled = player.shuffleModeEnabled,
        shuffleOrder = currentShuffleSequence(),
    )

    private fun scheduleSave() {
        if (!restoreDone) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val queue = snapshot()
            withContext(Dispatchers.IO) { runCatching { queueStore.save(queue) } }
        }
    }

    /** Leálláskor: a pillanatkép most készül, az írás az alkalmazás hatókörében fut le. */
    private fun saveInBackground() {
        if (!restoreDone || session == null) return
        saveJob?.cancel()
        val queue = snapshot()
        container.appScope.launch(Dispatchers.IO) { runCatching { queueStore.save(queue) } }
    }

    /** A mentett várólista a még elérhető számokkal, és a mentett módok. */
    private suspend fun loadRestorable(): Pair<RestoredQueue<Track>, SavedQueue>? {
        val saved = withContext(Dispatchers.IO) { queueStore.load() } ?: return null
        val available = container.library.tracksByIds(saved.trackIds)
        val restored = QueueLogic.restore(saved, available) ?: return null
        return restored to saved
    }

    private suspend fun restoreQueue() {
        try {
            if (player.mediaItemCount > 0) return
            val (restored, saved) = loadRestorable() ?: return
            // Ha közben a felhasználó már elindított valamit, az élvez elsőbbséget.
            if (player.mediaItemCount > 0) return
            player.setMediaItems(restored.items.map { it.toMediaItem() }, restored.startIndex, restored.startPositionMs)
            restored.shuffleOrder?.let { applyShuffleSequence(it) }
            player.repeatMode = saved.repeatMode
            player.shuffleModeEnabled = saved.shuffleEnabled
            // Előkészítés lejátszás nélkül: a felület látja a számot és a hosszát, de nem szól.
            player.playWhenReady = false
            player.prepare()
        } catch (e: Exception) {
            Log.w(TAG, "A várólista visszaállítása nem sikerült", e)
        } finally {
            restoreDone = true
        }
    }

    // --- Fejhallgató és Bluetooth ---

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!pausedByDisconnect || !headsetSettings.resumeOnReconnect) return
            if (SystemClock.elapsedRealtime() - pausedByDisconnectAt > RESUME_WINDOW_MS) {
                pausedByDisconnect = false
                return
            }
            if (addedDevices.any { it.isSink && it.type in headsetTypes() }) {
                pausedByDisconnect = false
                player.play()
            }
        }
    }

    private fun headsetTypes(): Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    // --- Munkamenet ---

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Az értesítésben és a zárolási képernyőn megjelenő ugrógombok (ahol a rendszer mutatja őket). */
    private fun mediaButtons(): ImmutableList<CommandButton> {
        val back = CommandButton.Builder(backIcon(skipSettings.backSeconds))
            .setDisplayName(getString(R.string.cd_skip_back, skipSettings.backSeconds))
            .setSessionCommand(PlaybackCommands.skipBack)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        val forward = CommandButton.Builder(forwardIcon(skipSettings.forwardSeconds))
            .setDisplayName(getString(R.string.cd_skip_forward, skipSettings.forwardSeconds))
            .setSessionCommand(PlaybackCommands.skipForward)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return ImmutableList.of(back, forward)
    }

    private fun backIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun forwardIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }

    private fun broadcastError(title: String, stopped: Boolean) {
        val args = Bundle().apply {
            putString(PlaybackCommands.EXTRA_TITLE, title)
            putBoolean(PlaybackCommands.EXTRA_STOPPED, stopped)
        }
        session?.broadcastCustomCommand(PlaybackCommands.playbackError, args)
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                )
            ) {
                scheduleSave()
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                updateWidget()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            effects.attach(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) playCounter.onDurationKnown(player.duration)
            if (playbackState == Player.STATE_ENDED && sleepTimer.state == SleepTimerState.EndOfTrack) {
                sleepTimer.onTrackEnded()
                onSleepTimerChanged()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                skipController.onSeekReported(newPosition.mediaItem?.mediaId, newPosition.positionMs)
                if (newPosition.mediaItemIndex == oldPosition.mediaItemIndex &&
                    newPosition.positionMs < RESTART_POSITION_MS
                ) {
                    playCounter.onSeekToStart(player.isPlaying)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            skipController.reset()
            playCounter.onItemStarted(mediaItem?.mediaId, player.duration.takeIf { it != C.TIME_UNSET } ?: 0L, player.isPlaying)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedByDisconnect = !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            if (pausedByDisconnect) pausedByDisconnectAt = SystemClock.elapsedRealtime()
            // „Az aktuális szám végén” időzítő: a lejátszó a szám végén megállt.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                sleepTimer.onTrackEnded()
                onSleepTimerChanged()
                // „Leállás az aktuális szám végén”: a következő szám elejére állunk (szünetben),
                // így a lejátszógomb azzal folytatja; az utolsó számnál az aktuális elejére.
                if (playbackPrefs.stopAfterCurrent) {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0L)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0
            playCounter.onPlayingChanged(isPlaying)
            playCountJob?.cancel()
            playCountJob = if (isPlaying) {
                scope.launch {
                    while (isActive) {
                        delay(PLAY_COUNT_CHECK_MS)
                        playCounter.check()
                    }
                }
            } else {
                null
            }
            // Lejátszás közben időnként a pozíciót is mentjük (váratlan leállás esetére).
            periodicSaveJob?.cancel()
            periodicSaveJob = if (isPlaying) {
                scope.launch {
                    while (isActive) {
                        delay(PERIODIC_SAVE_MS)
                        scheduleSave()
                    }
                }
            } else {
                null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            Log.w(TAG, "Lejátszási hiba: $title", error)
            consecutiveErrors++
            val canSkip = player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount
            if (canSkip) {
                // A hibás számot átlépjük, de csak annyiszor, ahány elem van: nincs végtelen próbálkozás.
                broadcastError(title, stopped = false)
                player.seekToNextMediaItem()
                player.prepare()
            } else {
                // Megállunk; a felhasználó választhat másik számot, vagy újra megnyomhatja a lejátszást.
                broadcastError(title, stopped = true)
                consecutiveErrors = 0
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Más alkalmazás vezérlője szűkebb alapkészletet kap; az ugrás mindenkinek elérhető.
            val base = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS
            }
            val commands = base.buildUpon()
                .add(PlaybackCommands.skipBack)
                .add(PlaybackCommands.skipForward)
                .apply {
                    // Időzítő és újratöltés csak a saját felületünknek.
                    if (controller.packageName == packageName) {
                        add(PlaybackCommands.sleepTimerSet)
                        add(PlaybackCommands.sleepTimerCancel)
                        add(PlaybackCommands.reloadTracks)
                        add(PlaybackCommands.playKeepCurrent)
                    }
                }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(mediaButtons())
                .setSessionExtras(sleepTimer.state.toBundle())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val step = args.getLong(PlaybackCommands.EXTRA_STEP_MS, 0L).takeIf { it > 0 }
            when (customCommand.customAction) {
                PlaybackCommands.ACTION_SKIP_BACK -> performSkip(SkipDirection.BACK, step)
                PlaybackCommands.ACTION_SKIP_FORWARD -> performSkip(SkipDirection.FORWARD, step)
                PlaybackCommands.ACTION_SLEEP_TIMER_SET -> setSleepTimer(
                    durationMs = args.getLong(PlaybackCommands.EXTRA_DURATION_MS, 0L).takeIf { it > 0 },
                    endOfTrack = args.getBoolean(PlaybackCommands.EXTRA_END_OF_TRACK, false),
                )
                PlaybackCommands.ACTION_SLEEP_TIMER_CANCEL -> setSleepTimer(durationMs = null, endOfTrack = false)
                PlaybackCommands.ACTION_RELOAD_TRACKS -> {
                    val ids = args.getLongArray(PlaybackCommands.EXTRA_TRACK_IDS)?.toSet().orEmpty()
                    scope.launch { reloadTracks(ids) }
                }
                PlaybackCommands.ACTION_PLAY_KEEP_CURRENT -> {
                    val ids = args.getLongArray(PlaybackCommands.EXTRA_TRACK_IDS)?.toList().orEmpty()
                    val shuffle = args.getBoolean(PlaybackCommands.EXTRA_SHUFFLE, false)
                    scope.launch { playKeepingCurrent(ids, shuffle) }
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item -> resolve(item) }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** A rendszer (pl. Bluetooth-lejátszógomb, értesítés újraindítás után) folytatást kér. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val restored = runCatching { loadRestorable() }.getOrNull()?.first
                if (restored == null) {
                    future.setException(UnsupportedOperationException("Nincs mentett várólista"))
                } else {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            restored.items.map { it.toMediaItem() },
                            restored.startIndex,
                            restored.startPositionMs,
                        ),
                    )
                }
            }
            return future
        }
    }

    /** A vezérlőtől érkező elemhez visszaállítja a lejátszható URI-t. */
    private fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri
            ?: item.mediaId.toLongOrNull()?.let { Track.contentUriFor(it) }
            ?: return null
        return item.buildUpon().setUri(uri).build()
    }

    companion object {
        /** Fut-e a szolgáltatás ebben a folyamatban (a widget ebből tudja, hogy a mentett állapot friss-e). */
        @Volatile
        var isRunning: Boolean = false
            private set

        private var current: java.lang.ref.WeakReference<PlaybackService>? = null

        /**
         * A widget gombja (fő szálon hívva). Igaz, ha a futó lejátszó kezelte; hamis, ha a szolgáltatás nem fut.
         * Ugyanazt a munkamenet-lejátszót használja, mint az értesítés és a felület.
         */
        fun handleWidgetKey(keyCode: Int): Boolean {
            val service = current?.get() ?: return false
            val sessionPlayer = service.session?.player ?: return false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> androidx.media3.common.util.Util.handlePlayPauseButtonAction(sessionPlayer)
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> sessionPlayer.seekToNext()
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> sessionPlayer.seekToPrevious()
                // A widget ugrógombjai: ugyanaz a közös ugrási logika, mint a többi vezérlőé.
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> sessionPlayer.seekBack()
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> sessionPlayer.seekForward()
                else -> return false
            }
            return true
        }

        private const val TAG = "PlaybackService"
        private const val SAVE_DEBOUNCE_MS = 1_000L
        private const val PERIODIC_SAVE_MS = 10_000L
        private const val PLAY_COUNT_CHECK_MS = 1_000L

        /** Ennél korábbra tekerve a szám „elejére” tekertünk (lejátszásszámláláshoz). */
        private const val RESTART_POSITION_MS = 1_000L

        /** Visszacsatlakozáskor csak ennyin belül folytatja automatikusan (30 perc). */
        private const val RESUME_WINDOW_MS = 30 * 60 * 1000L
    }
}
