package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.playlists.PlaylistFileParser
import io.github.x1wer.szabadzene.data.playlists.PlaylistFileWriter
import io.github.x1wer.szabadzene.data.playlists.PlaylistMatcher
import io.github.x1wer.szabadzene.data.playlists.StorageLocation
import io.github.x1wer.szabadzene.data.settings.LibraryLayout
import io.github.x1wer.szabadzene.data.settings.LibrarySection
import io.github.x1wer.szabadzene.playback.QueueLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Listaexport (M3U8), a zenetár füleinek beállítása és a „mostani szám végigszól” várólista-terv. */
class PlaylistExportTest {

    private fun track(id: Long, title: String, artist: String?, path: String?, durationMs: Long = 200_000) = Track(
        id = id,
        title = title,
        artist = artist,
        album = null,
        albumId = 0,
        durationMs = durationMs,
        displayName = path?.substringAfterLast('/') ?: "$title.mp3",
        path = path,
        relativePath = path?.let { StorageLocation.fromAbsolutePath(it)?.path?.substringBeforeLast('/', "")?.plus("/") },
        sizeBytes = 1,
        mimeType = "audio/mpeg",
        dateAddedSeconds = 0,
        genre = null,
        trackNumber = 0,
        year = 0,
    )

    private val song1 = track(1, "Árvíztűrő", "Kovács Éva", "/storage/emulated/0/Music/Kovács Éva/Album/01 Árvíztűrő.mp3")
    private val song2 = track(2, "Tükörfúrógép", null, "/storage/emulated/0/Download/Tükörfúrógép.flac", durationMs = 61_499)
    private val sdSong = track(3, "Kártyáról", "SD", "/storage/1a2b-3c4d/Zene/k.mp3")

    // --- Helyek és útvonalak ---

    @Test
    fun storageLocationsFromPathsAndDocumentIds() {
        assertEquals(StorageLocation("primary", "Music/a.mp3"), StorageLocation.fromAbsolutePath("/storage/emulated/0/Music/a.mp3"))
        assertEquals(StorageLocation("primary", "Music/a.mp3"), StorageLocation.fromAbsolutePath("/sdcard/Music/a.mp3"))
        assertEquals(StorageLocation("1A2B-3C4D", "Zene/k.mp3"), StorageLocation.fromAbsolutePath("/storage/1a2b-3c4d/Zene/k.mp3"))
        assertNull(StorageLocation.fromAbsolutePath("/data/user/0/x.mp3"))
        assertNull(StorageLocation.fromAbsolutePath(null))

        val ext = "com.android.externalstorage.documents"
        assertEquals(StorageLocation("primary", "Music/Listák"), StorageLocation.fromDocumentId(ext, "primary:Music/Listák"))
        assertEquals(StorageLocation("primary", ""), StorageLocation.fromDocumentId(ext, "primary:"))
        assertEquals(StorageLocation("1A2B-3C4D", "Zene"), StorageLocation.fromDocumentId(ext, "1a2b-3c4d:Zene"))
        assertEquals(
            StorageLocation("primary", "Download/Listák"),
            StorageLocation.fromDocumentId("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/Listák"),
        )
        assertNull(StorageLocation.fromDocumentId("com.android.providers.downloads.documents", "msf:123"))
    }

    @Test
    fun relativePathsBetweenFolders() {
        assertEquals("../Album/a.mp3", PlaylistFileWriter.relativePath("Music/Listák", "Music/Album/a.mp3"))
        assertEquals("a.mp3", PlaylistFileWriter.relativePath("Music", "Music/a.mp3"))
        assertEquals("Music/a.mp3", PlaylistFileWriter.relativePath("", "Music/a.mp3"))
        assertEquals("../../Download/b.flac", PlaylistFileWriter.relativePath("Music/Listák", "Download/b.flac"))
        // A mappa neve egyezhet a fájléval: a fájlnév sosem számít közös mappának.
        assertEquals("../x", PlaylistFileWriter.relativePath("Music/x", "Music/x"))
    }

    @Test
    fun locationIsRelativeOnSameVolumeAndAbsoluteOtherwise() {
        val target = StorageLocation("primary", "Music/Listák")
        assertEquals("../Kovács Éva/Album/01 Árvíztűrő.mp3", PlaylistFileWriter.locationFor(song1, target))
        assertEquals("/storage/1a2b-3c4d/Zene/k.mp3", PlaylistFileWriter.locationFor(sdSong, target))
        // Ismeretlen célmappa (pl. Letöltések-szolgáltató): teljes útvonal.
        assertEquals(song1.path, PlaylistFileWriter.locationFor(song1, null))
        val noPath = track(9, "Csak név", null, path = null).copy(relativePath = "Music/X/")
        assertEquals("Music/X/Csak név.mp3", PlaylistFileWriter.locationFor(noPath, null))
    }

    // --- Fájlnév és tartalom ---

    @Test
    fun fileNamesAreSafeAndUnique() {
        assertEquals("Buli.m3u8", PlaylistFileWriter.fileNameFor("Buli"))
        assertEquals("A_B_ C_.m3u8", PlaylistFileWriter.fileNameFor("A/B: C?"))
        assertEquals("Playlist.m3u8", PlaylistFileWriter.fileNameFor("  ..  "))
        assertEquals(100 + ".m3u8".length, PlaylistFileWriter.fileNameFor("x".repeat(300)).length)
        assertEquals("Buli.m3u8", PlaylistFileWriter.uniqueFileName("Buli.m3u8", listOf("Más.m3u8")))
        assertEquals("Buli (2).m3u8", PlaylistFileWriter.uniqueFileName("Buli.m3u8", listOf("buli.M3U8")))
        assertEquals("Buli (3).m3u8", PlaylistFileWriter.uniqueFileName("Buli.m3u8", listOf("Buli.m3u8", "Buli (2).m3u8")))
    }

    @Test
    fun writesExtendedM3u() {
        val text = PlaylistFileWriter.m3u8("Buli\nesti", listOf(song1, song2), StorageLocation("primary", "Music/Listák"))
        assertEquals(
            listOf(
                "#EXTM3U",
                "#PLAYLIST:Buli esti",
                "#EXTINF:200,Kovács Éva - Árvíztűrő",
                "../Kovács Éva/Album/01 Árvíztűrő.mp3",
                "#EXTINF:61,Tükörfúrógép",
                "../../Download/Tükörfúrógép.flac",
            ),
            text.trimEnd('\n').split('\n'),
        )
    }

    @Test
    fun exportedPlaylistImportsBackToTheSameTracks() {
        val library = listOf(song1, song2, sdSong, track(4, "Más", "X", "/storage/emulated/0/Music/Más/01 Árvíztűrő.mp3"))
        val target = StorageLocation("primary", "Music/Listák")
        val text = PlaylistFileWriter.m3u8("Vegyes", listOf(sdSong, song1, song2), target)
        val parsed = PlaylistFileParser.parse(PlaylistFileParser.decode(text.toByteArray(Charsets.UTF_8)), "Vegyes.m3u8")
        assertEquals("Vegyes", parsed.name)
        val match = PlaylistMatcher(library).match(parsed, target.path)
        assertEquals(listOf(3L, 1L, 2L), match.trackIds)
        assertEquals(3, match.total)
    }

    // --- A zenetár fülei ---

    @Test
    fun libraryLayoutKeepsOrderAndAtLeastOneTab() {
        assertEquals(LibrarySection.entries, LibraryLayout.decode(null).sections)
        assertEquals(listOf(LibrarySection.TRACKS, LibrarySection.GENRES), LibraryLayout.decode("GENRES, TRACKS,UNKNOWN").sections)
        assertEquals(LibrarySection.entries, LibraryLayout.decode("").sections)
        assertEquals("ALBUMS,ARTISTS", LibraryLayout.encode(listOf(LibrarySection.ARTISTS, LibrarySection.ALBUMS)))
        assertFalse(LibraryLayout(listOf(LibrarySection.ALBUMS)).showTabRow)
        assertTrue(LibraryLayout().showTabRow)
    }

    // --- „A mostani szám végigszól” ---

    @Test
    fun keepCurrentInOrderContinuesAfterTheCurrentTrack() {
        val plan = QueueLogic.keepCurrentPlan(listOf(10, 20, 30, 40), currentId = 30, shuffle = false, random = Random(1))
        assertEquals(listOf(10L, 20L), plan.before)
        assertEquals(listOf(40L), plan.after)
        assertEquals(2, plan.currentIndex)
        assertNull(plan.shuffleOrder)
    }

    @Test
    fun keepCurrentNotInListStartsTheListAfterIt() {
        val plan = QueueLogic.keepCurrentPlan(listOf(10, 20), currentId = 99, shuffle = false, random = Random(1))
        assertEquals(emptyList<Long>(), plan.before)
        assertEquals(listOf(10L, 20L), plan.after)
        assertEquals(0, plan.currentIndex)
    }

    @Test
    fun keepCurrentShuffledStartsWithCurrentThenAllOthers() {
        val ids = (1L..50L).toList()
        val plan = QueueLogic.keepCurrentPlan(ids, currentId = 17, shuffle = true, random = Random(42))
        val order = plan.shuffleOrder!!
        assertEquals(plan.currentIndex, order.first())
        assertEquals(16, plan.currentIndex)
        assertEquals((0 until 50).toSet(), order.toSet())
        assertEquals(50, order.size)
        // Valóban kevert (nem az alapsorrend).
        assertFalse(order.drop(1) == (0 until 50).filter { it != 16 })
    }
}
