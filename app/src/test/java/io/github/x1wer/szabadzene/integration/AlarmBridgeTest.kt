package io.github.x1wer.szabadzene.integration

import androidx.media3.common.Player
import io.github.x1wer.szabadzene.integration.AlarmBridge.BridgeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmBridgeTest {

    @Test
    fun `repeat mode as seen by the alarm app`() {
        assertEquals("ALL", AlarmBridge.repeatName(Player.REPEAT_MODE_ALL, stopAfterCurrent = false))
        assertEquals("ONE", AlarmBridge.repeatName(Player.REPEAT_MODE_ONE, stopAfterCurrent = false))
        assertEquals("OFF", AlarmBridge.repeatName(Player.REPEAT_MODE_OFF, stopAfterCurrent = false))
        assertEquals("OFF", AlarmBridge.repeatName(null, stopAfterCurrent = false))
        // A „leállás a szám végén” mód a lejátszó módjától függetlenül érvényes.
        assertEquals("STOP_AFTER_CURRENT", AlarmBridge.repeatName(Player.REPEAT_MODE_ALL, stopAfterCurrent = true))
    }

    @Test
    fun `missing files are skipped and positions stay contiguous`() {
        val known = mapOf(
            1L to BridgeTrack(1, "Egy", "A", 1000),
            3L to BridgeTrack(3, "Három", null, 3000),
        )
        val ordered = AlarmBridge.orderedTracks(listOf(3, 2, 1, 3), known)
        assertEquals(listOf(0 to 3L, 1 to 1L, 2 to 3L), ordered.map { it.first to it.second.id })
    }

    @Test
    fun `request paths`() {
        assertEquals(12L, AlarmBridge.playlistIdFrom(listOf("playlists", "12", "tracks")))
        assertEquals(AlarmBridge.FAVORITES_ID, AlarmBridge.playlistIdFrom(listOf("playlists", "0", "tracks")))
        assertNull(AlarmBridge.playlistIdFrom(listOf("playlists", "-1", "tracks")))
        assertNull(AlarmBridge.playlistIdFrom(listOf("playlists", "x", "tracks")))
        assertNull(AlarmBridge.playlistIdFrom(listOf("playlists")))
        assertEquals(42L, AlarmBridge.audioIdFrom(listOf("audio", "42")))
        assertNull(AlarmBridge.audioIdFrom(listOf("audio", "0")))
        assertNull(AlarmBridge.audioIdFrom(listOf("audio", "42", "x")))
    }
}
