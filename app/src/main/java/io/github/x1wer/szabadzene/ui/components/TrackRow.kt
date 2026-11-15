package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R

/** A számsor menüterületének legkisebb mérete (szélesség és magasság). */
val TrackMenuTouchSize = 64.dp

/** A sor legkisebb magassága – a menüterülethez igazodik. */
val TrackRowMinHeight = 64.dp

object TrackRowTags {
    const val ROW = "track_row"
    const val BODY = "track_row_body"
    const val MENU = "track_row_menu"
}

/**
 * Az alkalmazás egyetlen számsor-komponense. Minden számlista ezt használja.
 *
 * A sor két, egymást nem fedő részből áll: a bal oldali törzs elindítja a számot,
 * a jobb oldali, legalább 64 × 64 dp-s menüterület csak a menüt nyitja meg.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    trackId: Long?,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opcionális bal oldali elem (pl. átrendező fogantyú); nem fedi a lejátszó törzset. */
    leading: (@Composable () -> Unit)? = null,
) {
    val menuDescription = stringResource(R.string.cd_track_actions, title)
    Row(
        modifier = modifier
            .testTag(TrackRowTags.ROW)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = TrackRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Row(
            modifier = Modifier
                .testTag(TrackRowTags.BODY)
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onPlay)
                .padding(start = if (leading == null) 16.dp else 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                trackId = trackId,
                sizePx = 144,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (isCurrent) {
                        // Az első sorhoz igazítva, hogy hosszú, tördelt címnél se lógjon középen.
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 3.dp, end = 4.dp)
                                .size(18.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A menüterület rögzített szélességű, és a sor teljes magasságát kitölti.
        Box(
            modifier = Modifier
                .testTag(TrackRowTags.MENU)
                .width(TrackMenuTouchSize)
                .fillMaxHeight()
                .heightIn(min = TrackMenuTouchSize)
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = menuDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
