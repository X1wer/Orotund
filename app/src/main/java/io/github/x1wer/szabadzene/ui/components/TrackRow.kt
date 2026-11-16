package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R

/** A számsor menüterületének legkisebb mérete (szélesség és magasság). */
val TrackMenuTouchSize = 64.dp

/** A menüterület tényleges szélessége: a 64 dp-s legkisebb méretnél valamivel szélesebb. */
val TrackMenuWidth = 72.dp

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
 * A sor két, egymást nem fedő részből áll: a bal oldali törzs elindítja a számot (kijelölő módban
 * ki-/bejelöli), a jobb oldali, legalább 64 × 64 dp-s menüterület csak a menüt nyitja meg.
 * A sorok alján vékony elválasztó vonal fut.
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
    /** Opcionális bal oldali elem (pl. átrendező fogantyú, jelölőnégyzet); nem fedi a lejátszó törzset. */
    leading: (@Composable () -> Unit)? = null,
    /** Hosszan nyomva (pl. a kijelölő mód indítása); null esetén nincs hosszú nyomás. */
    onLongPress: (() -> Unit)? = null,
    /** Kijelölő módban a kijelölt sor kiemelt háttért kap. */
    selected: Boolean = false,
    showDivider: Boolean = true,
) {
    val menuDescription = stringResource(R.string.cd_track_actions, title)
    Row(
        modifier = modifier
            .testTag(TrackRowTags.ROW)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = TrackRowMinHeight)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .rowDivider(MaterialTheme.colorScheme.outlineVariant, showDivider),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Row(
            modifier = Modifier
                .testTag(TrackRowTags.BODY)
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (onLongPress != null) {
                        Modifier.combinedClickable(onClick = onPlay, onLongClick = onLongPress)
                    } else {
                        Modifier.clickable(onClick = onPlay)
                    },
                )
                .semantics { if (onLongPress != null) this.selected = selected }
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
                            modifier = Modifier.padding(top = 3.dp, end = 4.dp),
                            size = 18.dp,
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
        RowMenuArea(
            description = menuDescription,
            tag = TrackRowTags.MENU,
            highlighted = isCurrent,
            onClick = onMenu,
        )
    }
}

/** Vékony elválasztó vonal a sor alján (a téma `outlineVariant` színével), hogy a sorok ne folyjanak egybe. */
fun Modifier.rowDivider(color: Color, show: Boolean = true, startInset: Dp = 16.dp, endInset: Dp = 12.dp): Modifier =
    if (!show) this else drawBehind { drawRowDivider(color, startInset.toPx(), endInset.toPx()) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRowDivider(color: Color, start: Float, end: Float) {
    val stroke = 1.dp.toPx()
    val y = size.height - stroke / 2
    drawLine(color, Offset(start, y), Offset(size.width - end, y), strokeWidth = stroke)
}

/**
 * A szám- és gyűjteménysorok közös menüterülete: legalább 64 dp magas, [TrackMenuWidth] széles, a teljes
 * felülete a menüt nyitja. Közepén egy tónusos körben a „további műveletek” ikon.
 */
@Composable
fun RowMenuArea(description: String, tag: String, highlighted: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val circle = scaledIconSize(DefaultIconSize) + 16.dp
    Box(
        modifier = Modifier
            .testTag(tag)
            .width(TrackMenuWidth)
            .fillMaxHeight()
            .heightIn(min = TrackMenuTouchSize)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = TrackMenuWidth / 2),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(circle)
                .clip(CircleShape)
                .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = null,
                tint = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}
