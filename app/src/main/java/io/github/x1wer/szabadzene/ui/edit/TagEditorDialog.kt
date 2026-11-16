package io.github.x1wer.szabadzene.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.graphics.scale
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.edit.CoverChange
import io.github.x1wer.szabadzene.data.edit.TagSnapshot
import io.github.x1wer.szabadzene.data.edit.TagValues
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.components.TrackArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Címke- és borítószerkesztő teljes képernyős ablak. A fájl tényleges címkéit mutatja
 * ([load] a fájlból olvas); mentéskor a hívó szerzi meg az engedélyt és végzi az ellenőrzött írást.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    track: Track,
    load: suspend () -> TagSnapshot,
    saving: Boolean,
    error: String?,
    onSave: (TagValues, CoverChange) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<TagSnapshot?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var values by remember { mutableStateOf(TagValues()) }
    var cover by remember { mutableStateOf<CoverChange>(CoverChange.Keep) }
    var imageError by remember { mutableStateOf(false) }
    val imageErrorText = stringResource(R.string.error_image)

    LaunchedEffect(track.id) {
        runCatching { load() }
            .onSuccess {
                loaded = it
                values = it.values
            }
            .onFailure { loadFailed = true }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { prepareCover(context, uri) }.getOrNull() }
                if (bytes == null) {
                    imageError = true
                } else {
                    imageError = false
                    cover = CoverChange.Replace(bytes, "image/jpeg")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // A teljes képernyős ablak saját rendszersáv-beállítást kap: az ikonok az app témájához
        // igazodjanak (világos háttéren sötét ikonok), és a gomb ne csússzon a navigációs sáv alá.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = lightSurface
                controller.isAppearanceLightNavigationBars = lightSurface
            }
        }
        Surface(Modifier.fillMaxSize().testTag("tag_editor")) {
            Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.tags_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !saving) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dialog_cancel))
                        }
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Text(track.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    when {
                        loadFailed -> Text(stringResource(R.string.tags_read_failed), color = MaterialTheme.colorScheme.error)
                        loaded == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(R.string.tags_loading))
                        }
                        else -> {
                            CoverEditor(
                                track = track,
                                original = loaded?.cover,
                                cover = cover,
                                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onRemove = { cover = CoverChange.Remove },
                                enabled = !saving,
                            )
                            if (imageError) Text(imageErrorText, color = MaterialTheme.colorScheme.error)
                            Field(R.string.tag_title, values.title, "tag_field_title", !saving) { values = values.copy(title = it) }
                            Field(R.string.tag_artist, values.artist, "tag_field_artist", !saving) { values = values.copy(artist = it) }
                            Field(R.string.tag_album, values.album, "tag_field_album", !saving) { values = values.copy(album = it) }
                            Field(R.string.tag_album_artist, values.albumArtist, "tag_field_album_artist", !saving) {
                                values = values.copy(albumArtist = it)
                            }
                            Field(R.string.tag_genre, values.genre, "tag_field_genre", !saving) { values = values.copy(genre = it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) {
                                    Field(R.string.tag_year, values.year, "tag_field_year", !saving, number = true) {
                                        values = values.copy(year = it.filter(Char::isDigit).take(4))
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    Field(R.string.tag_track, values.trackNumber, "tag_field_track", !saving, number = true) {
                                        values = values.copy(trackNumber = it.filter(Char::isDigit).take(4))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tags_save_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tag_error"))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.tags_saving), modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = { onSave(values.normalized(), cover) },
                        enabled = loaded != null && !saving,
                        modifier = Modifier.testTag("tag_save"),
                    ) { Text(stringResource(R.string.dialog_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverEditor(
    track: Track,
    original: ByteArray?,
    cover: CoverChange,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    Text(stringResource(R.string.tag_cover), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(12.dp)
        val modifier = Modifier
            .size(96.dp)
            .clip(shape)
        when (cover) {
            is CoverChange.Replace -> {
                val bitmap = remember(cover) { BitmapFactory.decodeByteArray(cover.bytes, 0, cover.bytes.size)?.asImageBitmap() }
                if (bitmap != null) Image(bitmap, null, modifier, contentScale = ContentScale.Crop)
            }
            CoverChange.Remove -> Box(modifier, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tag_cover_none), style = MaterialTheme.typography.labelSmall)
            }
            CoverChange.Keep -> if (original != null) {
                TrackArtwork(trackId = track.id, sizePx = 256, modifier = Modifier.size(96.dp), cornerRadius = 12.dp)
            } else {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tag_cover_none), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.testTag("tag_cover_pick")) {
                Text(stringResource(R.string.tag_cover_change))
            }
            val hasCover = cover is CoverChange.Replace || (cover == CoverChange.Keep && original != null)
            if (hasCover) {
                OutlinedButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("tag_cover_remove")) {
                    Text(stringResource(R.string.tag_cover_remove))
                }
            }
        }
    }
    if (cover is CoverChange.Replace) {
        Text(stringResource(R.string.tag_cover_new), style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Field(
    label: Int,
    value: String,
    tag: String,
    enabled: Boolean,
    number: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(tag),
    )
}

/** A választott kép JPEG-gé alakítva, legfeljebb 1000 képpont oldalhosszal (a fájl ne nőjön feleslegesen). */
private fun prepareCover(context: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_COVER_PX) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = MAX_COVER_PX.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
    } else {
        decoded
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}

private const val MAX_COVER_PX = 1000
