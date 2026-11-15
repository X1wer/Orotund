package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.playback.SkipDirection

/** Az ugrógombok legkisebb érintési területe. */
val SkipButtonMinSize = 56.dp

object SkipButtonTags {
    const val BACK = "skip_back"
    const val FORWARD = "skip_forward"

    fun forDirection(direction: SkipDirection) = when (direction) {
        SkipDirection.BACK -> BACK
        SkipDirection.FORWARD -> FORWARD
    }
}

@Composable
private fun skipLabel(direction: SkipDirection, seconds: Int): String = when (direction) {
    SkipDirection.BACK -> stringResource(R.string.skip_back_label, seconds)
    SkipDirection.FORWARD -> stringResource(R.string.skip_forward_label, seconds)
}

@Composable
private fun skipDescription(direction: SkipDirection, seconds: Int): String = when (direction) {
    SkipDirection.BACK -> stringResource(R.string.cd_skip_back, seconds)
    SkipDirection.FORWARD -> stringResource(R.string.cd_skip_forward, seconds)
}

@Composable
private fun SkipIcon(direction: SkipDirection, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Replay,
        contentDescription = null,
        // Előre: a körkörös nyíl tükrözve.
        modifier = if (direction == SkipDirection.FORWARD) {
            modifier.graphicsLayer(scaleX = -1f)
        } else {
            modifier
        },
    )
}

/**
 * Állandóan látható ugrógomb felirattal („−10 mp” / „+10 mp”). Legalább 56 × 56 dp.
 * Ha nem lehet tekerni, látható marad, de inaktív.
 */
@Composable
fun SkipButton(
    direction: SkipDirection,
    seconds: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    val description = skipDescription(direction, seconds)
    val unavailable = stringResource(R.string.cd_unavailable)
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
            .testTag(SkipButtonTags.forDirection(direction))
            .sizeIn(minWidth = 112.dp, minHeight = SkipButtonMinSize)
            .semantics {
                contentDescription = description
                if (!enabled) stateDescription = unavailable
            },
    ) {
        if (showIcon) {
            SkipIcon(direction, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        // Nagyon nagy betűméretnél a felirat két sorba törhet, de nem vágódik le.
        Text(
            text = skipLabel(direction, seconds),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Tömör, 56 × 56 dp-s ugrógomb a minilejátszó egysoros elrendezéséhez.
 * A másodpercérték a gombon belül olvasható; a mérete nem nő a betűmérettel,
 * ezért ezt az elrendezést csak normál betűméretnél használjuk.
 */
@Composable
fun CompactSkipButton(
    direction: SkipDirection,
    seconds: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = skipDescription(direction, seconds)
    val unavailable = stringResource(R.string.cd_unavailable)
    val label = skipLabel(direction, seconds)
    val density = LocalDensity.current
    // Rögzített méretű szöveg, hogy a 56 dp-s gombban mindig elférjen.
    val valueSize = with(density) { 15.dp.toSp() }
    val unitSize = with(density) { 10.dp.toSp() }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .testTag(SkipButtonTags.forDirection(direction))
            .size(SkipButtonMinSize)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) {
                    stateDescription = unavailable
                    disabled()
                }
                onClick(label = description) {
                    if (enabled) onClick()
                    enabled
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.heightIn(min = SkipButtonMinSize),
        ) {
            val sign = if (direction == SkipDirection.BACK) "−" else "+"
            Text(
                text = "$sign$seconds",
                fontSize = valueSize,
                lineHeight = valueSize,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.skip_unit),
                fontSize = unitSize,
                lineHeight = unitSize,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
