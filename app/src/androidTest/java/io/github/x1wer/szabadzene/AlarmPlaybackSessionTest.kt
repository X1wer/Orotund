package io.github.x1wer.szabadzene

import android.content.ComponentName
import android.os.Bundle
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.integration.AlarmBridge
import io.github.x1wer.szabadzene.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Ébresztés a Zengőben: a munkamenet-parancsok a valódi lejátszószolgáltatáson át (AlarmBridge 2). */
@RunWith(AndroidJUnit4::class)
class AlarmPlaybackSessionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    @Test
    fun alarmCommandsAreOfferedAndAnEmptyListChangesNothing() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = onMain {
            MediaController.Builder(context, token).setApplicationLooper(Looper.getMainLooper()).buildAsync()
        }.get(15, TimeUnit.SECONDS)
        try {
            AlarmBridge.ALARM_ACTIONS.forEach { action ->
                assertTrue(action, onMain { controller.isSessionCommandAvailable(command(action)) })
            }
            val volumeBefore = onMain { controller.volume }

            // Nem létező lista: nincs mit lejátszani, a lejátszó változatlan marad.
            val args = Bundle().apply { putLong(AlarmBridge.EXTRA_PLAYLIST_ID, 987_654_321L) }
            val play = onMain { controller.sendCustomCommand(command(AlarmBridge.ACTION_ALARM_PLAY), args) }
                .get(15, TimeUnit.SECONDS)
            assertEquals(SessionError.ERROR_BAD_VALUE, play.resultCode)
            assertEquals(volumeBefore, onMain { controller.volume }, 0.001f)

            // Befejezés futó ébresztés nélkül: ártalmatlan.
            val finish = onMain { controller.sendCustomCommand(command(AlarmBridge.ACTION_ALARM_FINISH), Bundle.EMPTY) }
                .get(15, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, finish.resultCode)
        } finally {
            onMain { controller.release() }
        }
    }
}
