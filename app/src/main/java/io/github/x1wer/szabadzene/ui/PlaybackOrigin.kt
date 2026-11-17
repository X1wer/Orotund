package io.github.x1wer.szabadzene.ui

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.ui.library.CollectionType
import kotlinx.coroutines.launch

/**
 * Honnan indult a lejátszás: a fő képernyő füle és azon belül a képernyő útvonala (null = a fül kezdőképernyője).
 * A widgetre koppintva ez a képernyő nyílik meg (pl. a szóló lejátszási lista vagy a Zenetár).
 */
data class PlaybackOrigin(val tab: Int, val route: String?) {
    fun encode(): String = if (route == null) "$tab" else "$tab|$route"

    companion object {
        const val TAB_LIBRARY = 0
        const val TAB_PLAYLISTS = 1
        const val TAB_FOLDERS = 2

        val LIBRARY = PlaybackOrigin(TAB_LIBRARY, null)

        /** Hibás vagy hiányzó érték: null (ilyenkor a widget csak megnyitja az appot). */
        fun decode(text: String?): PlaybackOrigin? {
            if (text.isNullOrBlank()) return null
            val tab = text.substringBefore('|').toIntOrNull()?.takeIf { it in TAB_LIBRARY..TAB_FOLDERS } ?: return null
            val route = text.substringAfter('|', "").ifEmpty { null }
            return PlaybackOrigin(tab, route)
        }

        fun playlist(id: Long) = PlaybackOrigin(TAB_PLAYLISTS, playlistRoute(id))

        /** Album, előadó, műfaj a Zenetár fülön; mappa a Mappák fülön. */
        fun collection(type: CollectionType, key: String) = if (type == CollectionType.FOLDER) {
            PlaybackOrigin(TAB_FOLDERS, collectionRoute("folders", type, key))
        } else {
            PlaybackOrigin(TAB_LIBRARY, collectionRoute("library", type, key))
        }
    }
}

fun playlistRoute(id: Long) = "playlists/playlist/$id"

fun collectionRoute(prefix: String, type: CollectionType, key: String) =
    "$prefix/collection/${type.name}/${Uri.encode(key)}"

/** Az éppen látható képernyő forrása (az AppRoot adja képernyőnként); innen indított lejátszás ezt jegyzi meg. */
val LocalPlaybackOrigin = staticCompositionLocalOf { PlaybackOrigin.LIBRARY }

/** A lejátszás forrásának megjegyzése (a háttérben, az alkalmazás hatókörében). */
fun AppContainer.recordPlaybackOrigin(origin: PlaybackOrigin) {
    appScope.launch { settings.setPlaybackOrigin(origin.encode()) }
}
