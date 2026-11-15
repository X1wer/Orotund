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
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.edit.TagSaveResult
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.MenuState
import io.github.x1wer.szabadzene.ui.components.ConfirmDialog
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
                        val result = editor.saveTags(track, values, cover)
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

    // --- Törlés a telefonról ---
    fun afterDelete(track: Track, gone: Boolean) {
        scope.launch {
            if (gone) {
                editor.afterDeleted(track)
                notify(resources.getString(R.string.toast_file_deleted, track.displayName))
            } else {
                notify(resources.getString(R.string.error_delete))
            }
        }
    }
    menu.deleteFile?.let { track ->
        ConfirmDialog(
            title = stringResource(R.string.delete_file_title),
            message = stringResource(
                R.string.delete_file_body,
                track.displayName,
                Formatter.formatShortFileSize(context, track.sizeBytes),
            ),
            confirmLabel = stringResource(R.string.delete_file_confirm),
            onConfirm = {
                menu.deleteFile = null
                if (editor.usesSystemWriteRequest) {
                    // Android 11+: a rendszer még egyszer rákérdez, és jóváhagyáskor ő maga töröl.
                    onGranted = { scope.launch { afterDelete(track, editor.isGone(track)) } }
                    onDenied = { notify(resources.getString(R.string.error_delete)) }
                    systemRequest.launch(IntentSenderRequest.Builder(editor.deleteRequest(listOf(track))).build())
                } else {
                    withWriteAccess(
                        null,
                        forNewFile = false,
                        denied = { notify(resources.getString(R.string.error_permission_storage)) },
                    ) {
                        scope.launch { afterDelete(track, editor.deleteLegacy(track)) }
                    }
                }
            },
            onDismiss = { menu.deleteFile = null },
        )
    }
}
