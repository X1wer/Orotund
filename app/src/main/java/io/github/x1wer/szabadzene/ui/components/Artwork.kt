package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.ui.LocalAppContainer

/**
 * Egy szám borítója. Hiányzó borítónál egységes helyettesítő ikon látszik.
 * A méretet a hívó adja meg a modifierrel; [sizePx] a betöltött kép felbontása.
 */
@Composable
fun TrackArtwork(
    trackId: Long?,
    sizePx: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val loader = LocalAppContainer.current?.artwork
    val image: ImageBitmap? by produceState(
        initialValue = trackId?.let { loader?.peek(it, sizePx) },
        trackId,
        sizePx,
    ) {
        value = if (trackId == null || loader == null) null else loader.load(trackId, sizePx)
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}
