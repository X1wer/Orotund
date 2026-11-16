package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R

object CollectionRowTags {
    const val BODY = "collection_row_body"
    const val MENU = "collection_row_menu"
}

/**
 * Album, előadó, műfaj, mappa vagy lejátszási lista sora. Ugyanaz a felépítés, mint a
 * számsornál: a törzs megnyitja a gyűjteményt, a jobb oldali, legalább 64 × 64 dp-s
 * menüterület csak a menüt nyitja meg.
 */
@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    artworkTrackId: Long?,
    onOpen: () -> Unit,
    onMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val menuDescription = stringResource(R.string.cd_collection_actions, title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = TrackRowMinHeight)
            .rowDivider(MaterialTheme.colorScheme.outlineVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .testTag(CollectionRowTags.BODY)
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 32.dp)
                }
            } else {
                TrackArtwork(trackId = artworkTrackId, sizePx = 144, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMenu != null) {
            RowMenuArea(
                description = menuDescription,
                tag = CollectionRowTags.MENU,
                highlighted = false,
                onClick = onMenu,
            )
        }
    }
}
