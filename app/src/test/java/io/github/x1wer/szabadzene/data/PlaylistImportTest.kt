package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.playlists.ParsedPlaylist
import io.github.x1wer.szabadzene.data.playlists.PlaylistFileEntry
import io.github.x1wer.szabadzene.data.playlists.PlaylistFileParser
import io.github.x1wer.szabadzene.data.playlists.PlaylistFormat
import io.github.x1wer.szabadzene.data.playlists.PlaylistImporter
import io.github.x1wer.szabadzene.data.playlists.PlaylistMatcher
import io.github.x1wer.szabadzene.data.playlists.PlaylistPaths
import io.github.x1wer.szabadzene.data.playlists.uniquePlaylistName
import io.github.x1wer.szabadzene.data.settings.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Listafájlok olvasása és párosítása a zenetárral; a betű- és ikonméret beállítás szabályai. */
class PlaylistImportTest {

    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        displayName: String,
        relativePath: String?,
        durationMs: Long = 200_000,
        withPath: Boolean = true,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = null,
        albumId = 0,
        durationMs = durationMs,
        displayName = displayName,
        path = if (withPath && relativePath != null) "/storage/emulated/0/$relativePath$displayName" else null,
        relativePath = relativePath,
        sizeBytes = 1,
        mimeType = "audio/mpeg",
        dateAddedSeconds = 0,
        genre = null,
        trackNumber = 0,
        year = 0,
    )

    private val library = listOf(
        track(1, "Árvíztűrő", "Kovács Éva", "01 Árvíztűrő.mp3", "Music/Kovács Éva/Album/"),
        track(2, "Tükörfúrógép", "Kovács Éva", "02 Tükörfúrógép.mp3", "Music/Kovács Éva/Album/", durationMs = 185_000),
        track(3, "Intro", "A", "01.mp3", "Music/A/"),
        track(4, "Intro", "B", "01.mp3", "Music/B/"),
        track(5, "Egyedi", "C", "egyedi.flac", "Download/", withPath = false),
    )
    private val matcher = PlaylistMatcher(library)

    // --- Olvasás ---

    @Test
    fun parsesExtendedM3uWithNameTitlesAndBom() {
        val text = "\uFEFF#EXTM3U\n#PLAYLIST:Buli\n#EXTINF:185,Kovács Éva - Tükörfúrógép\n" +
            "../Kovács Éva/Album/02 Tükörfúrógép.mp3\n\n# megjegyzés\n#EXTINF:-1,Rádió\nhttp://example.invalid/stream\n" +
            "C:\\Zene\\A\\01.mp3\n"
        val parsed = PlaylistFileParser.parse(text, "edzés.m3u8")
        assertEquals("Buli", parsed.name)
        assertEquals(3, parsed.entries.size)
        assertEquals(PlaylistFileEntry("../Kovács Éva/Album/02 Tükörfúrógép.mp3", "Kovács Éva - Tükörfúrógép", 185), parsed.entries[0])
        assertNull(parsed.entries[1].durationSeconds)
        assertEquals("C:\\Zene\\A\\01.mp3", parsed.entries[2].location)
        assertNull(parsed.entries[2].title)
    }

    @Test
    fun parsesPlsInIndexOrder() {
        val text = "[playlist]\nNumberOfEntries=2\nFile2=/sdcard/Music/A/01.mp3\nTitle2=Intro\nLength2=200\n" +
            "File1=egyedi.flac\nLength1=-1\nVersion=2\n"
        val parsed = PlaylistFileParser.parse(text, "Lista.pls")
        assertEquals("Lista", parsed.name)
        assertEquals(listOf("egyedi.flac", "/sdcard/Music/A/01.mp3"), parsed.entries.map { it.location })
        assertEquals(listOf(null, 200), parsed.entries.map { it.durationSeconds })
        assertEquals("Intro", parsed.entries[1].title)
    }

    @Test
    fun decodesUtf8AndFallsBackToWindows1250() {
        assertEquals("Árvíztűrő", PlaylistFileParser.decode("Árvíztűrő".toByteArray(Charsets.UTF_8)))
        val legacy = "Árvíztűrő tükörfúrógép".toByteArray(charset("windows-1250"))
        assertEquals("Árvíztűrő tükörfúrógép", PlaylistFileParser.decode(legacy))
    }

    @Test
    fun recognisesPlaylistExtensions() {
        assertEquals(PlaylistFormat.M3U, PlaylistFormat.forFileName("a.M3U8"))
        assertEquals(PlaylistFormat.PLS, PlaylistFormat.forFileName("b.pls"))
        assertFalse(PlaylistFormat.isPlaylistFile("c.mp3"))
    }

    // --- Útvonalak ---

    @Test
    fun cleansFileUrisWindowsPathsAndStreams() {
        assertEquals("/storage/emulated/0/Music/a b.mp3", PlaylistPaths.clean("file:///storage/emulated/0/Music/a%20b.mp3"))
        assertEquals("C:/Zene/a+b.mp3", PlaylistPaths.clean("file:///C:/Zene/a+b.mp3"))
        assertEquals("../Zene/a.mp3", PlaylistPaths.clean("..\\Zene\\a.mp3"))
        assertNull(PlaylistPaths.clean("https://example.invalid/a.mp3"))
        assertNull(PlaylistPaths.clean("   "))
        assertEquals("Music/A/01.mp3", PlaylistPaths.storageRelative("/storage/emulated/0/Music/A/01.mp3"))
        assertEquals("Music/x.mp3", PlaylistPaths.storageRelative("/storage/1A2B-3C4D/Music/x.mp3"))
        assertEquals("Music/x.mp3", PlaylistPaths.storageRelative("/sdcard/Music/x.mp3"))
        assertNull(PlaylistPaths.storageRelative("C:/Zene/x.mp3"))
        assertEquals("Music/B/x.mp3", PlaylistPaths.collapse("Music/Lists/../B/./x.mp3"))
    }

    @Test
    fun documentIdGivesPlaylistFolder() {
        val external = "com.android.externalstorage.documents"
        assertEquals("Music/Listák", PlaylistImporter.dirFromDocumentId(external, "primary:Music/Listák/a.m3u"))
        assertEquals("", PlaylistImporter.dirFromDocumentId(external, "primary:a.m3u"))
        assertEquals("Download", PlaylistImporter.dirFromDocumentId("x", "raw:/storage/emulated/0/Download/a.m3u"))
        assertNull(PlaylistImporter.dirFromDocumentId("com.android.providers.downloads.documents", "msf:12"))
    }

    // --- Párosítás ---

    private fun find(location: String, dir: String? = null, title: String? = null, seconds: Int? = null): Long? =
        matcher.find(PlaylistFileEntry(location, title, seconds), dir)?.id

    @Test
    fun matchesAbsoluteRelativeAndForeignPaths() {
        assertEquals(1L, find("/storage/emulated/0/Music/Kovács Éva/Album/01 Árvíztűrő.mp3"))
        assertEquals(1L, find("/sdcard/music/kovács éva/album/01 árvíztűrő.MP3"))
        assertEquals(2L, find("../Kovács Éva/Album/02 Tükörfúrógép.mp3", dir = "Music/Listák"))
        // Más gépen készült lista: a vége egyezik.
        assertEquals(2L, find("D:\\Zene\\Kovács Éva\\Album\\02 Tükörfúrógép.mp3"))
        assertEquals(3L, find("C:/Zene/A/01.mp3"))
        // Útvonal nélküli szám (pl. Android 9 előtti adatnál): a fájlnév egyedi.
        assertEquals(5L, find("egyedi.flac"))
    }

    @Test
    fun ambiguousFileNameAndWrongDurationAreNotMatched() {
        assertNull(find("01.mp3"))
        assertNull(find("Valahol/01.mp3"))
        assertNull(find("02 Tükörfúrógép.mp3", seconds = 300))
        assertEquals(2L, find("02 Tükörfúrógép.mp3", seconds = 186))
    }

    @Test
    fun fallsBackToUniqueTitle() {
        assertEquals(2L, find("Ismeretlen/mappa/x.mp3", title = "kovacs eva -  tukorfurogep"))
        assertEquals(1L, find("x.mp3", title = "ÁRVÍZTŰRŐ"))
        assertNull(find("x.mp3", title = "Intro"))
        assertNull(find("http://example.invalid/a.mp3"))
    }

    @Test
    fun matchKeepsFileOrderAndCountsMissing() {
        val playlist = ParsedPlaylist(
            "Teszt",
            listOf(
                PlaylistFileEntry("../Kovács Éva/Album/02 Tükörfúrógép.mp3"),
                PlaylistFileEntry("hiányzó.mp3"),
                PlaylistFileEntry("/storage/emulated/0/Music/A/01.mp3"),
                PlaylistFileEntry("/storage/emulated/0/Music/A/01.mp3"),
            ),
        )
        val result = matcher.match(playlist, "Music/Listák")
        assertEquals(listOf(2L, 3L, 3L), result.trackIds)
        assertEquals(3, result.found)
        assertEquals(4, result.total)
    }

    @Test
    fun uniqueNamesGetSuffix() {
        assertEquals("Buli", uniquePlaylistName("Buli", listOf("Edzés")))
        assertEquals("Buli (2)", uniquePlaylistName(" Buli ", listOf("buli")))
        assertEquals("Buli (3)", uniquePlaylistName("Buli", listOf("Buli", "Buli (2)")))
    }

    // --- Betű- és ikonméret ---

    @Test
    fun scaleSettingsAreSanitizedAndCapped() {
        assertEquals(100, AppearanceSettings.sanitizeScale(null))
        assertEquals(100, AppearanceSettings.sanitizeScale(120))
        assertEquals(130, AppearanceSettings.sanitizeScale(130))
        assertEquals(1.5f, AppearanceSettings.effectiveFontScale(1f, 150), 0.001f)
        assertEquals(1.15f, AppearanceSettings.effectiveFontScale(1f, 115), 0.001f)
        // A rendszer 2,0×-es betűjét a saját szorzó nem viszi tovább.
        assertEquals(2f, AppearanceSettings.effectiveFontScale(2f, 150), 0.001f)
        assertEquals(0.7f, AppearanceSettings.effectiveFontScale(0.8f, 85), 0.001f)
        assertTrue(AppearanceSettings.ALLOWED_SCALE_PERCENTS.contains(100))
    }
}
