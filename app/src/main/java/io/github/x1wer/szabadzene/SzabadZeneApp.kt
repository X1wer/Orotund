package io.github.x1wer.szabadzene

import android.app.Application
import android.content.Context
import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.playlists.PlaylistRepository
import io.github.x1wer.szabadzene.data.settings.SettingsRepository
import io.github.x1wer.szabadzene.playback.PlayerConnection
import io.github.x1wer.szabadzene.ui.components.ArtworkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SzabadZeneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.library.start()
    }
}

/** Az alkalmazás egyetlen példányban létező összetevői (egyszerű, kézi függőségkezelés). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val library = LibraryRepository(
        context = context.applicationContext,
        dao = database.trackDao(),
        filter = settings.libraryFilter,
        scope = appScope,
    )
    val playlists = PlaylistRepository(database, library)
    val artwork = ArtworkLoader(context.applicationContext)
    val player = PlayerConnection(context.applicationContext, appScope)
}
