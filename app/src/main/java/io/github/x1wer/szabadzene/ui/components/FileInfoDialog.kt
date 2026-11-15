package io.github.x1wer.szabadzene.ui.components

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.formatDuration
import io.github.x1wer.szabadzene.ui.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TechnicalInfo(val bitrateKbps: Int?, val sampleRateHz: Int?)

private fun readTechnicalInfo(context: Context, track: Track): TechnicalInfo {
    var bitrate: Int? = null
    var sampleRate: Int? = null
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, track.contentUri)
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.let { it / 1000 }
        } finally {
            retriever.release()
        }
    }
    runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, track.contentUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                    format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    break
                }
            }
        } finally {
            extractor.release()
        }
    }
    return TechnicalInfo(bitrate, sampleRate)
}

/** A fájl adatai. A műszaki adatokat a háttérben olvassa ki; ami nem elérhető, azt „—” jelzi. */
@Composable
fun FileInfoDialog(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<TechnicalInfo?>(null, track.id) {
        value = withContext(Dispatchers.IO) { readTechnicalInfo(context, track) }
    }
    val unknown = stringResource(R.string.file_info_unknown)
    val location = track.path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
        ?: track.relativePath
        ?: unknown
    val rows = listOf(
        stringResource(R.string.file_info_name) to track.displayName,
        stringResource(R.string.file_info_location) to location,
        stringResource(R.string.file_info_artist) to (track.artist ?: unknown),
        stringResource(R.string.file_info_album) to (track.album ?: unknown),
        stringResource(R.string.file_info_duration) to
            (if (track.durationMs > 0) formatDuration(track.durationMs) else unknown),
        stringResource(R.string.file_info_size) to formatFileSize(track.sizeBytes),
        stringResource(R.string.file_info_format) to (track.mimeType ?: unknown),
        stringResource(R.string.file_info_bitrate) to
            (info?.bitrateKbps?.let { stringResource(R.string.file_info_kbps, it) } ?: unknown),
        stringResource(R.string.file_info_sample_rate) to
            (info?.sampleRateHz?.let { stringResource(R.string.file_info_hz, it) } ?: unknown),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
        title = { Text(stringResource(R.string.file_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (label, value) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}
