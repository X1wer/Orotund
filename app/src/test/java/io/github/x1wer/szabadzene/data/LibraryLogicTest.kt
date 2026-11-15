package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.TrackSearchIndex
import io.github.x1wer.szabadzene.data.library.applyFilter
import io.github.x1wer.szabadzene.data.library.folderPath
import io.github.x1wer.szabadzene.data.library.groupAlbums
import io.github.x1wer.szabadzene.data.library.groupArtists
import io.github.x1wer.szabadzene.data.library.groupFolders
import io.github.x1wer.szabadzene.data.library.searchKey
import io.github.x1wer.szabadzene.data.library.sortTracks
import io.github.x1wer.szabadzene.data.settings.LibraryFilter
import io.github.x1wer.szabadzene.data.settings.LibrarySort
import io.github.x1wer.szabadzene.data.settings.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        album: String? = null,
        albumId: Long = 0,
        durationMs: Long = 60_000,
        path: String? = "/storage/emulated/0/Music/$title.mp3",
        dateAdded: Long = id,
        trackNumber: Int = 0,
        genre: String? = null,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        displayName = path?.substringAfterLast('/') ?: "$title.mp3",
        path = path,
        relativePath = null,
        sizeBytes = 1,
        mimeType = "audio/mpeg",
        dateAddedSeconds = dateAdded,
        genre = genre,
        trackNumber = trackNumber,
        year = 0,
    )

    // --- Keresés ---

    @Test
    fun searchKeyRemovesAccentsAndCase() {
        assertEquals("arvizturo tukorfurogep", searchKey("ÁRVÍZTŰRŐ tükörfúrógép"))
        assertEquals("oszi", searchKey("Őszi"))
    }

    @Test
    fun searchIsAccentAndCaseInsensitiveBothWays() {
        val tracks = listOf(
            track(1, "Árvíztűrő tükörfúrógép", artist = "Kovács Éva"),
            track(2, "arvizturo", artist = "Teszt"),
            track(3, "Más dal", artist = "Valaki"),
        )
        val index = TrackSearchIndex(tracks)
        assertEquals(listOf(1L, 2L), index.search("arvizturo").map { it.id })
        assertEquals(listOf(1L, 2L), index.search("ÁRVÍZTŰRŐ").map { it.id })
        assertEquals(listOf(1L), index.search("kovacs eva").map { it.id })
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun searchMatchesAlbumAndFileNameAndRequiresAllWords() {
        val tracks = listOf(
            track(1, "Tavaszi szél", artist = "Szabó Ádám", album = "Évszakok", path = "/m/03 Tavaszi szel.m4a"),
            track(2, "Őszi reggel", artist = "Szabó Ádám", album = "Évszakok", path = "/m/05 Oszi reggel.ogg"),
        )
        val index = TrackSearchIndex(tracks)
        assertEquals(listOf(1L, 2L), index.search("evszakok").map { it.id })
        assertEquals(listOf(2L), index.search("05 oszi").map { it.id })
        assertEquals(listOf(1L), index.search("szabo tavaszi").map { it.id })
        assertTrue(index.search("szabo nyari").isEmpty())
    }

    // --- Rendezés ---

    @Test
    fun titleSortFollowsHungarianAlphabet() {
        val titles = listOf("Zebra", "Őszi", "Alma", "Öröm", "Ostor", "Ábel")
        val sorted = sortTracks(titles.mapIndexed { i, t -> track(i.toLong(), t) }, LibrarySort(SortField.TITLE))
        assertEquals(listOf("Ábel", "Alma", "Ostor", "Öröm", "Őszi", "Zebra"), sorted.map { it.title })
    }

    @Test
    fun artistSortPutsUnknownLastAndDescendingReverses() {
        val tracks = listOf(track(1, "B", artist = null), track(2, "A", artist = "Zoli"), track(3, "C", artist = "Ádám"))
        assertEquals(listOf(3L, 2L, 1L), sortTracks(tracks, LibrarySort(SortField.ARTIST)).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sortTracks(tracks, LibrarySort(SortField.ARTIST, descending = true)).map { it.id })
    }

    @Test
    fun durationAndDateSorts() {
        val tracks = listOf(
            track(1, "a", durationMs = 300_000, dateAdded = 10),
            track(2, "b", durationMs = 30_000, dateAdded = 30),
            track(3, "c", durationMs = 120_000, dateAdded = 20),
        )
        assertEquals(listOf(2L, 3L, 1L), sortTracks(tracks, LibrarySort(SortField.DURATION)).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), sortTracks(tracks, LibrarySort(SortField.DATE_ADDED, descending = true)).map { it.id })
    }

    // --- Szűrés ---

    @Test
    fun excludedFolderHidesItsSubfoldersButNotSimilarNames() {
        val tracks = listOf(
            track(1, "a", path = "/sdcard/Music/Ringtones/a.mp3"),
            track(2, "b", path = "/sdcard/Music/Ringtones/Old/b.mp3"),
            track(3, "c", path = "/sdcard/Music/RingtonesX/c.mp3"),
            track(4, "d", path = "/sdcard/Music/d.mp3"),
        )
        val visible = applyFilter(tracks, LibraryFilter(excludedFolders = setOf("/sdcard/Music/Ringtones")))
        assertEquals(listOf(3L, 4L), visible.map { it.id })
    }

    @Test
    fun minimumDurationHidesShortFiles() {
        val tracks = listOf(track(1, "rövid", durationMs = 3_000), track(2, "hosszú", durationMs = 45_000))
        assertEquals(listOf(1L, 2L), applyFilter(tracks, LibraryFilter(minDurationSeconds = 0)).map { it.id })
        assertEquals(listOf(2L), applyFilter(tracks, LibraryFilter(minDurationSeconds = 10)).map { it.id })
        assertEquals(10, LibraryFilter.sanitizeMinDuration(10))
        assertEquals(0, LibraryFilter.sanitizeMinDuration(7))
    }

    // --- Csoportosítás ---

    @Test
    fun albumsGroupByIdAndSortTracksByNumber() {
        val tracks = listOf(
            track(1, "Harmadik", album = "Évszakok", albumId = 5, trackNumber = 3, artist = "Szabó Ádám"),
            track(2, "Első", album = "Évszakok", albumId = 5, trackNumber = 1, artist = "Szabó Ádám"),
            track(3, "Második", album = "Évszakok", albumId = 5, trackNumber = 2, artist = "Kórus"),
            track(4, "Egyedül", album = null, albumId = 9),
        )
        val albums = groupAlbums(tracks)
        assertEquals(listOf("Évszakok", null), albums.map { it.name })
        assertEquals(listOf(2L, 3L, 1L), albums[0].tracks.map { it.id })
        assertEquals("Szabó Ádám", albums[0].detail)
    }

    @Test
    fun sameAlbumWithDifferentMediaStoreIdsInOneFolderIsOneAlbum() {
        // A MediaStore az elrontott kódolású címke miatt két azonosítót adhat ugyanannak az albumnak.
        val tracks = listOf(
            track(1, "a", album = "Ékezetes dalok", albumId = 1, path = "/m/Teszt/a.mp3"),
            track(2, "b", album = "Ékezetes dalok", albumId = 2, path = "/m/Teszt/b.mp3"),
            track(3, "c", album = "Ékezetes dalok", albumId = 2, path = "/m/Masik/c.mp3"),
        )
        val albums = groupAlbums(tracks)
        assertEquals(2, albums.size)
        assertEquals(listOf(1L, 2L), albums.first { it.key.endsWith("/m/Teszt") }.tracks.map { it.id })
    }

    @Test
    fun artistsGroupCaseInsensitively() {
        val tracks = listOf(track(1, "a", artist = "Kórus"), track(2, "b", artist = "kórus"), track(3, "c", artist = null))
        val artists = groupArtists(tracks)
        assertEquals(2, artists.size)
        assertEquals(2, artists[0].tracks.size)
        assertEquals(null, artists[1].name)
    }

    @Test
    fun foldersUseParentDirectory() {
        val tracks = listOf(
            track(1, "a", path = "/sdcard/Music/Teszt/a.mp3"),
            track(2, "b", path = "/sdcard/Music/Teszt/b.mp3"),
            track(3, "c", path = "/sdcard/Download/c.mp3"),
        )
        assertEquals("/sdcard/Music/Teszt", tracks[0].folderPath)
        val folders = groupFolders(tracks)
        assertEquals(listOf("Download", "Teszt"), folders.map { it.name })
        assertEquals("/sdcard/Music/Teszt", folders[1].key)
        assertEquals(2, folders[1].tracks.size)
    }
}
