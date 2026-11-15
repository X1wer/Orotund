package io.github.x1wer.szabadzene

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.data.settings.AppearanceSettings
import io.github.x1wer.szabadzene.data.settings.ThemeMode
import io.github.x1wer.szabadzene.ui.AppRoot
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.theme.SzabadZeneTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    /** Növekvő számláló: minden érték egy „nyisd meg a lejátszót” kérés (pl. értesítésből). */
    private val openPlayerRequests = MutableStateFlow(0)

    private val container: AppContainer
        get() = (application as SzabadZeneApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // A rendszersávok ikonjai az app témáját kövessék, ne csak a rendszerét.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                SzabadZeneTheme(darkTheme = dark, dynamicColor = appearance.dynamicColor, accent = appearance.accent) {
                    AppRoot(openPlayerRequests = openPlayerRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
    }

    override fun onStop() {
        container.player.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            openPlayerRequests.value += 1
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "io.github.x1wer.szabadzene.OPEN_PLAYER"
    }
}
