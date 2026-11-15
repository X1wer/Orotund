package io.github.x1wer.szabadzene

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
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
            CompositionLocalProvider(LocalAppContainer provides container) {
                SzabadZeneTheme {
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
