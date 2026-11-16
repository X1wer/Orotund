package io.github.x1wer.szabadzene.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.x1wer.szabadzene.data.settings.AppearanceSettings

/** Az ikonok méretszorzója (Beállítások → Megjelenés → Ikonméret). */
val LocalIconScale = compositionLocalOf { 1f }

/**
 * A felhasználó betű- és ikonmérete. 100%-os betűméretnél a rendszer sűrűsége változatlan marad
 * (Android 14-től a nemlineáris betűskálázás is); egyébként a rendszer betűmérete szorzódik.
 */
@Composable
fun ProvideUiScale(textScalePercent: Int, iconScalePercent: Int, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = if (AppearanceSettings.sanitizeScale(textScalePercent) == 100) {
        base
    } else {
        Density(base.density, AppearanceSettings.effectiveFontScale(base.fontScale, textScalePercent))
    }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalIconScale provides AppearanceSettings.sanitizeScale(iconScalePercent) / 100f,
        content = content,
    )
}
