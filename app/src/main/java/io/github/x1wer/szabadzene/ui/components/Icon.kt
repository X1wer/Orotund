package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.ui.theme.LocalIconScale

/** Az ikonok alapmérete (a Material-ikonoké). */
val DefaultIconSize = 24.dp

/**
 * Az alkalmazás ikonja: a Material `Icon`, de a méretét ([size]) a felhasználó ikonméret-beállítása
 * szorozza. Az alkalmazásban minden ikon ezen keresztül jelenik meg; a méretet a [size] paraméterben
 * kell megadni, nem a módosítóban (különben a szorzó nem hatna rá).
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = DefaultIconSize,
) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(scaledIconSize(size)),
        tint = tint,
    )
}

/** Egy ikonméret a felhasználó ikonméret-szorzójával. */
@Composable
fun scaledIconSize(size: Dp): Dp = size * LocalIconScale.current
