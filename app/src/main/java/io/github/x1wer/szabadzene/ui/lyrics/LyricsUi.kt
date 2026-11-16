package io.github.x1wer.szabadzene.ui.lyrics

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.lyrics.LrcParser
import io.github.x1wer.szabadzene.data.lyrics.Lyrics
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object LyricsTags {
    const val ADD = "lyrics_add"
    const val EDIT = "lyrics_edit"
    const val EDITOR = "lyrics_editor"
    const val FIELD = "lyrics_field"
    const val SAVE = "lyrics_save"
    const val IMPORT = "lyrics_import"
}

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object Unsupported : LyricsLoad
    data class Loaded(val raw: String, val lyrics: Lyrics) : LyricsLoad
}

/** A dalszöveg fölött kihagyott hely (a szerkesztés gombja itt ül). */
private val TOP_GAP = 56.dp

/** Felhasználói görgetés után ennyi ideig nem görget magától a dalszöveg. */
private const val MANUAL_SCROLL_PAUSE_MS = 4_000L

/**
 * A lejátszott szám dalszövege (a fájlba ágyazott szöveg). Időzített (LRC) szövegnél az aktuális
 * sor kiemelve, a nézet követi, és egy sorra koppintva oda teker. Ha nincs dalszöveg, és a fájl
 * címkéje írható, felajánlja a hozzáadást.
 */
@Composable
fun LyricsPane(
    track: Track?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onEdit: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val editor = container?.fileEditor
    val version by (editor?.lyricsVersion ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val load by produceState<LyricsLoad>(LyricsLoad.Loading, track?.id, version) {
        value = LyricsLoad.Loading
        val raw = if (track == null || editor == null) null else editor.readLyrics(track)
        value = if (raw == null) {
            LyricsLoad.Unsupported
        } else {
            LyricsLoad.Loaded(raw, withContext(Dispatchers.Default) { LrcParser.parse(raw) })
        }
    }
    val writable = track != null && editor?.isTagWritable(track) == true
    Box(modifier) {
        when (val state = load) {
            LyricsLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LyricsLoad.Unsupported -> Message(stringResource(R.string.lyrics_unsupported), Modifier.align(Alignment.Center))
            is LyricsLoad.Loaded -> if (state.lyrics.isEmpty) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = null, size = 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.lyrics_none), textAlign = TextAlign.Center)
                    if (writable && track != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onEdit(track) }, modifier = Modifier.testTag(LyricsTags.ADD)) {
                            Text(stringResource(R.string.lyrics_add))
                        }
                    }
                }
            } else {
                if (state.lyrics.synced) {
                    SyncedLyrics(state.lyrics, positionMs, onSeek)
                } else {
                    Text(
                        text = state.lyrics.lines.joinToString("\n") { it.text },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = TOP_GAP, bottom = 12.dp),
                    )
                }
                if (writable && track != null) {
                    FilledTonalIconButton(
                        onClick = { onEdit(track) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(48.dp)
                            .testTag(LyricsTags.EDIT),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.lyrics_edit))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val current = lyrics.indexAt(positionMs)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastManualScroll by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastManualScroll = SystemClock.elapsedRealtime() }
    LaunchedEffect(current) {
        if (current >= 0 && SystemClock.elapsedRealtime() - lastManualScroll > MANUAL_SCROLL_PAUSE_MS) {
            // Az aktuális sor a felső harmad tájékán legyen.
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Felül hely a szerkesztés gombjának, hogy ne takarja az első sort.
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = TOP_GAP, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == current
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { line.timeMs?.let(onSeek) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Dalszöveg szerkesztése: kézzel, vagy .lrc/.txt fájlból betöltve. Mentéskor a hívó szerzi meg az
 * írási engedélyt, és a címkeírás ellenőrzött folyamatával menti a fájlba (üres szöveg = törlés).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorDialog(
    track: Track,
    saving: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf(false) }
    LaunchedEffect(track.id) {
        if (text == null) text = container?.fileEditor?.readLyrics(track).orEmpty()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) { runCatching { readTextFile(context, uri) }.getOrNull() }
                importError = imported == null
                if (imported != null) text = imported
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = lightSurface
                controller.isAppearanceLightNavigationBars = lightSurface
            }
        }
        Surface(
            Modifier
                .fillMaxSize()
                .testTag(LyricsTags.EDITOR),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                TopAppBar(
                    title = { Text(stringResource(R.string.lyrics_editor_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !saving) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dialog_cancel))
                        }
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    val current = text
                    if (current == null) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { text = it.take(MAX_LYRICS_CHARS) },
                            enabled = !saving,
                            placeholder = { Text(stringResource(R.string.lyrics_placeholder)) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag(LyricsTags.FIELD),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.lyrics_editor_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (importError) {
                        Text(stringResource(R.string.lyrics_import_failed), color = MaterialTheme.colorScheme.error)
                    }
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lyrics_error"))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("text/*", "application/octet-stream", "application/x-subrip")) },
                        enabled = !saving,
                        modifier = Modifier.testTag(LyricsTags.IMPORT),
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, size = 18.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.lyrics_import))
                    }
                    Spacer(Modifier.weight(1f))
                    if (saving) CircularProgressIndicator(Modifier.size(24.dp))
                    Button(
                        onClick = { text?.let(onSave) },
                        enabled = !saving && text != null,
                        modifier = Modifier.testTag(LyricsTags.SAVE),
                    ) { Text(stringResource(R.string.dialog_save)) }
                }
            }
        }
    }
}

private const val MAX_LYRICS_CHARS = 50_000
private const val MAX_IMPORT_BYTES = 512 * 1024

/**
 * Szövegfájl beolvasása: UTF-8 (BOM-mal vagy anélkül); ha az nem érvényes, közép-európai
 * (Windows-1250) kódolás, ami a régi magyar .lrc fájloknál gyakori.
 */
private fun readTextFile(context: Context, uri: Uri): String? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
        if (buffer.size > MAX_IMPORT_BYTES) return null
        buffer
    } ?: return null
    val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        bytes.copyOfRange(3, bytes.size)
    } else {
        bytes
    }
    val utf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        utf8.decode(ByteBuffer.wrap(body)).toString()
    } catch (_: CharacterCodingException) {
        String(body, Charset.forName("windows-1250"))
    }
}

/** Legfeljebb [limit] bájt beolvasása (az InputStream.readNBytes csak Android 13-tól van). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (read < 0) break
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}
