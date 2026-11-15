package io.github.x1wer.szabadzene.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    /** Null, amíg az adatbázis első eredménye meg nem érkezik. */
    val tracks: StateFlow<List<Track>?> = library.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isScanning: StateFlow<Boolean> = library.isScanning
    val hasScannedOnce: StateFlow<Boolean> = library.hasScannedOnce

    fun hasPermission(): Boolean = library.hasPermission()

    fun onPermissionGranted() = library.start()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.library) }
        }
    }
}
