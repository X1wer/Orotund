package io.github.x1wer.szabadzene.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.data.db.PlaylistSummary
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.TrackGroup
import io.github.x1wer.szabadzene.data.library.TrackSearchIndex
import io.github.x1wer.szabadzene.data.library.groupAlbums
import io.github.x1wer.szabadzene.data.library.groupArtists
import io.github.x1wer.szabadzene.data.library.groupFolders
import io.github.x1wer.szabadzene.data.library.groupGenres
import io.github.x1wer.szabadzene.data.library.sortTracks
import io.github.x1wer.szabadzene.data.settings.LibrarySort
import io.github.x1wer.szabadzene.data.stats.RankedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Típusok a gyűjtemény-részletező képernyőhöz. */
enum class CollectionType { ALBUM, ARTIST, GENRE, FOLDER }

/**
 * A zenetár képernyőinek közös állapota (az Activityhez kötve, így képernyőváltáskor és
 * forgatáskor nem számolódik újra).
 */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.library
    private val started = SharingStarted.WhileSubscribed(5_000)

    /** Null, amíg az adatbázis első eredménye meg nem érkezik. Cím szerint rendezve. */
    val tracks: StateFlow<List<Track>?> = library.tracks.stateIn(viewModelScope, started, null)

    /** Minden beolvasott szám (a kizárt mappákéval együtt) – a mappakezeléshez. */
    val allTracks: StateFlow<List<Track>> = library.allTracks.stateIn(viewModelScope, started, emptyList())

    val sort: StateFlow<LibrarySort> = container.settings.librarySort.stateIn(viewModelScope, started, LibrarySort())

    val sortedTracks: StateFlow<List<Track>?> = combine(library.tracks, container.settings.librarySort) { list, sort ->
        sortTracks(list, sort)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, started, null)

    val albums: StateFlow<List<TrackGroup>> = grouped { groupAlbums(it) }
    val artists: StateFlow<List<TrackGroup>> = grouped { groupArtists(it) }
    val genres: StateFlow<List<TrackGroup>> = grouped { groupGenres(it) }
    val folders: StateFlow<List<TrackGroup>> = grouped { groupFolders(it) }

    val searchIndex: StateFlow<TrackSearchIndex?> = library.tracks
        .map { TrackSearchIndex(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, started, null)

    val favoriteIds: StateFlow<Set<Long>> = container.playlists.favoriteIds.stateIn(viewModelScope, started, emptySet())
    val favoriteTracks: StateFlow<List<Track>> = container.playlists.favoriteTracks.stateIn(viewModelScope, started, emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = container.playlists.playlists.stateIn(viewModelScope, started, emptyList())

    /** Automatikus listák a lejátszási statisztikából. */
    val mostPlayed: StateFlow<List<RankedTrack>> = container.playStats.mostPlayed
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, started, emptyList())
    val recentlyPlayed: StateFlow<List<RankedTrack>> = container.playStats.recentlyPlayed
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, started, emptyList())

    val isScanning: StateFlow<Boolean> = library.isScanning
    val hasScannedOnce: StateFlow<Boolean> = library.hasScannedOnce

    private fun grouped(block: (List<Track>) -> List<TrackGroup>): StateFlow<List<TrackGroup>> =
        library.tracks.filterNotNull().map(block).flowOn(Dispatchers.Default).stateIn(viewModelScope, started, emptyList())

    /** Egy gyűjtemény élő tartalma a részletező képernyőhöz. */
    fun collection(type: CollectionType, key: String): Flow<TrackGroup?> {
        val source = when (type) {
            CollectionType.ALBUM -> albums
            CollectionType.ARTIST -> artists
            CollectionType.GENRE -> genres
            CollectionType.FOLDER -> folders
        }
        return source.map { groups -> groups.firstOrNull { it.key == key } }
    }

    fun hasPermission(): Boolean = library.hasPermission()

    fun onPermissionGranted() = library.start()

    fun rescan() = library.refresh()

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { container.settings.setSort(sort) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
