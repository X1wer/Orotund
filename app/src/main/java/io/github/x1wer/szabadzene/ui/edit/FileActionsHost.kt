package io.github.x1wer.szabadzene.ui.edit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.edit.TagSaveResult
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.MenuState
import io.github.x1wer.szabadzene.ui.components.ConfirmDialog
import io.github.x1wer.szabadzene.ui.lyrics.LyricsEditorDialog
import kotlinx.coroutines.launch

/**
 * A fájlműveletek ablakai és engedélyfolyamatai (címkeszerkesztés, vágás, csengőhang, törlés).
 *
 * Engedélyek API-szint szerint:
 * - Android 11+: módosítás és törlés előtt a rendszer saját, fájlonkénti kérése
 *   (`MediaStore.createWriteRequest` / `createDeleteRequest`); új fájl létrehozásához nem kell engedély.
 * - Android 10: `WRITE_EXTERNAL_STORAGE` (régi tárhelymód) a módosításhoz és a törléshez.
 * - Android 8–9: `WRITE_EXTERNAL_STORAGE` mindenhez, a vágott fájl mentéséhez is.
 * - Csengőhang: „Rendszerbeállítások módosítása” a rendszer saját képernyőjén, előtte magyarázat.
 */
@Composable
fun FileActionsHost(menu: MenuState, snackbar: SnackbarHostState) {
    val container = LocalAppContainer.current ?: return
    val editor = container.fileEditor
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    // Az engedélykérés után futó folytatás (megadva / elutasítva).
    var onGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onDenied by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun finish(granted: Boolean) {
        val next = if (granted) onGranted else onDenied
        onGranted = null
        onDenied = null
        next?.invoke()
    }

    val systemRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        finish(result.resultCode == Activity.RESULT_OK)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        finish(granted)
    }
    val writeSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // A rendszerképernyő nem ad érdemi eredménykódot: visszatéréskor újra ellenőrizzük.
        finish(editor.canWriteSettings())
    }

    fun notify(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    /** Írási engedély a meglévő fájlhoz (vagy Android 9-ig új fájlhoz), majd [action]. */
    fun withWriteAccess(track: Track?, forNewFile: Boolean, denied: () -> Unit, action: () -> Unit) {
        when {
            !forNewFile && track != null && editor.usesSystemWriteRequest -> {
                onGranted = action
                onDenied = denied
                systemRequest.launch(IntentSenderRequest.Builder(editor.writeRequest(listOf(track))).build())
            }
            editor.legacyWritePermissionMissing(forNewFile) -> {
                onGranted = action
                onDenied = denied
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            else -> action()
        }
    }

    // --- Címkék ---
    var tagSaving by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    menu.editTags?.let { track ->
        TagEditorDialog(
            track = track,
            load = { editor.readTags(track) },
            saving = tagSaving,
            error = tagError,
            onSave = { values, cover ->
                tagError = null
                withWriteAccess(
                    track,
                    forNewFile = false,
                    denied = { tagError = resources.getString(R.string.error_permission_write) },
                ) {
                    tagSaving = true
                    scope.launch {
                        // A dalszöveghez a címkeszerkesztő nem nyúl (azt a saját szerkesztője kezeli).
                        val result = editor.saveTags(track, values.copy(lyrics = null), cover)
                        tagSaving = false
                        when (result) {
                            TagSaveResult.Success -> {
                                menu.editTags = null
                                notify(resources.getString(R.string.toast_tags_saved))
                            }
                            is TagSaveResult.Failure -> tagError = resources.getString(
                                when {
                                    result.reason == "permission" -> R.string.error_permission_write
                                    !result.restored -> R.string.error_tags_not_restored
                                    result.reason == "verify" -> R.string.error_tags_verify
                                    else -> R.string.error_tags_write
                                },
                            )
                        }
                    }
                }
            },
            onDismiss = {
                menu.editTags = null
                tagError = null
            },
        )
    }

    // --- Dalszöveg ---
    var lyricsSaving by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    menu.editLyrics?.let { track ->
        LyricsEditorDialog(
            track = track,
            saving = lyricsSaving,
            error = lyricsError,
            onSave = { text ->
                lyricsError = null
                withWriteAccess(
                    track,
                    forNewFile = false,
                    denied = { lyricsError = resources.getString(R.string.error_permission_write) },
                ) {
                    lyricsSaving = true
                    scope.launch {
                        val result = editor.saveLyrics(track, text)
                        lyricsSaving = false
                        when (result) {
                            TagSaveResult.Success -> {
                                menu.editLyrics = null
                                notify(resources.getString(R.string.toast_lyrics_saved))
                            }
                            is TagSaveResult.Failure -> lyricsError = resources.getString(
                                when {
                                    result.reason == "permission" -> R.string.error_permission_write
                                    !result.restored -> R.string.error_tags_not_restored
                                    result.reason == "verify" -> R.string.error_tags_verify
                                    else -> R.string.error_tags_write
                                },
                            )
                        }
                    }
                }
            },
            onDismiss = {
                menu.editLyrics = null
                lyricsError = null
            },
        )
    }

    // --- Vágás ---
    var trimSaving by remember { mutableStateOf(false) }
    var trimError by remember { mutableStateOf<String?>(null) }
    menu.trim?.let { track ->
        TrimDialog(
            track = track,
            mode = editor.trimModeFor(track),
            saving = trimSaving,
            error = trimError,
            onPausePlayer = { container.player.pause() },
            onSave = { start, end, name ->
                trimError = null
                withWriteAccess(
                    null,
                    forNewFile = true,
                    denied = { trimError = resources.getString(R.string.error_permission_storage) },
                ) {
                    trimSaving = true
                    scope.launch {
                        val result = runCatching { editor.trim(track, start, end, name) }
                        trimSaving = false
                        result.onSuccess {
                            menu.trim = null
                            notify(resources.getString(R.string.toast_trim_saved, it.location))
                        }.onFailure {
                            trimError = resources.getString(R.string.error_trim)
                        }
                    }
                }
            },
            onDismiss = {
                menu.trim = null
                trimError = null
            },
        )
    }

    // --- Csengőhang ---
    fun applyRingtone(track: Track) {
        scope.launch {
            val ok = editor.setAsRingtone(track)
            notify(resources.getString(if (ok) R.string.toast_ringtone_set else R.string.error_ringtone, track.title))
        }
    }
    menu.ringtone?.let { track ->
        if (editor.canWriteSettings()) {
            LaunchedEffect(track) {
                menu.ringtone = null
                applyRingtone(track)
            }
        } else {
            AlertDialog(
                onDismissRequest = { menu.ringtone = null },
                modifier = Modifier.testTag("ringtone_explain"),
                title = { Text(stringResource(R.string.ringtone_permission_title)) },
                text = { Text(stringResource(R.string.ringtone_permission_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            menu.ringtone = null
                            onGranted = { applyRingtone(track) }
                            onDenied = { notify(resources.getString(R.string.ringtone_denied)) }
                            writeSettings.launch(
                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri()),
                            )
                        },
                        modifier = Modifier.testTag("ringtone_open_settings"),
                    ) { Text(stringResource(R.string.ringtone_open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { menu.ringtone = null }) { Text(stringResource(R.string.dialog_cancel)) }
                },
            )
        }
    }

    // --- Törlés a telefonról (egy vagy több fájl) ---
    fun afterDelete(tracks: List<Track>, gone: List<Track>) {
        scope.launch {
            if (gone.isNotEmpty()) editor.afterDeleted(gone)
            notify(
                when {
                    gone.isEmpty() -> resources.getString(R.string.error_delete)
                    gone.size < tracks.size -> resources.getString(R.string.error_delete_partial, gone.size, tracks.size)
                    gone.size == 1 -> resources.getString(R.string.toast_file_deleted, gone.first().displayName)
                    else -> resources.getQuantityString(R.plurals.toast_files_deleted, gone.size, gone.size)
                },
            )
        }
    }
    menu.deleteFiles?.takeIf { it.isNotEmpty() }?.let { tracks ->
        val single = tracks.singleOrNull()
        val totalSize = Formatter.formatShortFileSize(context, tracks.sumOf { it.sizeBytes })
        ConfirmDialog(
            title = if (single != null) {
                stringResource(R.string.delete_file_title)
            } else {
                pluralStringResource(R.plurals.delete_files_title, tracks.size, tracks.size)
            },
            message = if (single != null) {
                stringResource(R.string.delete_file_body, single.displayName, totalSize)
            } else {
                pluralStringResource(R.plurals.delete_files_body, tracks.size, tracks.size, totalSize)
            },
            confirmLabel = stringResource(if (single != null) R.string.delete_file_confirm else R.string.delete_files_confirm),
            onConfirm = {
                menu.deleteFiles = null
                if (editor.usesSystemWriteRequest) {
                    // Android 11+: a rendszer még egyszer rákérdez (egyszer, az összes fájlra), és ő maga töröl.
                    onGranted = { scope.launch { afterDelete(tracks, tracks.filter { editor.isGone(it) }) } }
                    onDenied = { notify(resources.getString(R.string.error_delete)) }
                    systemRequest.launch(IntentSenderRequest.Builder(editor.deleteRequest(tracks)).build())
                } else {
                    withWriteAccess(
                        null,
                        forNewFile = false,
                        denied = { notify(resources.getString(R.string.error_permission_storage)) },
                    ) {
                        scope.launch { afterDelete(tracks, tracks.filter { editor.deleteLegacy(it) }) }
                    }
                }
            },
            onDismiss = { menu.deleteFiles = null },
        )
    }
}
