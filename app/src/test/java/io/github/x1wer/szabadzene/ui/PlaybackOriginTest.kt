package io.github.x1wer.szabadzene.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A lejátszás forrásának tárolt alakja (a widget ebből nyitja meg a képernyőt). */
class PlaybackOriginTest {
    @Test
    fun encodesAndDecodesTabAndRoute() {
        assertEquals("0", PlaybackOrigin.LIBRARY.encode())
        assertEquals(PlaybackOrigin.LIBRARY, PlaybackOrigin.decode("0"))
        val playlist = PlaybackOrigin(PlaybackOrigin.TAB_PLAYLISTS, playlistRoute(-1))
        assertEquals("1|playlists/playlist/-1", playlist.encode())
        assertEquals(playlist, PlaybackOrigin.decode(playlist.encode()))
        val folder = PlaybackOrigin(PlaybackOrigin.TAB_FOLDERS, "folders/collection/FOLDER/Music%2FA%7CB")
        assertEquals(folder, PlaybackOrigin.decode(folder.encode()))
    }

    @Test
    fun invalidValuesAreIgnored() {
        assertNull(PlaybackOrigin.decode(null))
        assertNull(PlaybackOrigin.decode(""))
        assertNull(PlaybackOrigin.decode("7|x"))
        assertNull(PlaybackOrigin.decode("abc"))
    }
}
