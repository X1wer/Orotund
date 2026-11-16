package io.github.x1wer.szabadzene.data.playlists

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Egy listafájl importálásának eredménye. */
data class PlaylistImportOutcome(
    val fileName: String,
    /** A létrehozott lista neve (null, ha nem jött létre). */
    val playlistName: String?,
    val found: Int,
    val total: Int,
    val status: Status,
) {
    enum class Status { CREATED, NO_MATCH, UNREADABLE, NOT_PLAYLIST }
}

/**
 * Listafájlok (.m3u, .m3u8, .pls) importálása a rendszer fájlválasztójával kapott címekről: egy vagy
 * több fájl, vagy egy mappa összes listafájlja (almappákkal). Internet és tárhely-engedély nem kell.
 */
class PlaylistImporter(
    private val context: Context,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
) {
    private val resolver get() = context.contentResolver

    private data class Source(val uri: Uri, val fileName: String, val dir: String?)

    suspend fun importFiles(uris: List<Uri>): List<PlaylistImportOutcome> = withContext(Dispatchers.IO) {
        importAll(uris.map { uri -> Source(uri, displayName(uri) ?: uri.lastPathSegment.orEmpty(), dirOfDocument(uri)) })
    }

    suspend fun importFolder(treeUri: Uri): List<PlaylistImportOutcome> = withContext(Dispatchers.IO) {
        importAll(listTree(treeUri).sortedBy { it.fileName.lowercase() })
    }

    private suspend fun importAll(sources: List<Source>): List<PlaylistImportOutcome> {
        if (sources.isEmpty()) return emptyList()
        val matcher = PlaylistMatcher(library.allTracks.first())
        val names = playlists.playlists.first().map { it.name }.toMutableList()
        return sources.map { source ->
            if (!PlaylistFormat.isPlaylistFile(source.fileName)) {
                return@map PlaylistImportOutcome(source.fileName, null, 0, 0, PlaylistImportOutcome.Status.NOT_PLAYLIST)
            }
            val parsed = runCatching { read(source) }.getOrNull()
                ?: return@map PlaylistImportOutcome(source.fileName, null, 0, 0, PlaylistImportOutcome.Status.UNREADABLE)
            val match = matcher.match(parsed, source.dir)
            if (match.found == 0) {
                return@map PlaylistImportOutcome(source.fileName, null, 0, match.total, PlaylistImportOutcome.Status.NO_MATCH)
            }
            val name = uniquePlaylistName(parsed.name, names)
            names += name
            playlists.create(name, match.trackIds)
            PlaylistImportOutcome(source.fileName, name, match.found, match.total, PlaylistImportOutcome.Status.CREATED)
        }
    }

    private fun read(source: Source): ParsedPlaylist {
        val bytes = resolver.openInputStream(source.uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > PlaylistFileParser.MAX_BYTES) throw IOException("too large")
            }
            out.toByteArray()
        } ?: throw IOException("no stream")
        return PlaylistFileParser.parse(PlaylistFileParser.decode(bytes), source.fileName)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun dirOfDocument(uri: Uri): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        dirFromDocumentId(uri.authority, DocumentsContract.getDocumentId(uri))
    }.getOrNull()

    /** A mappa és almappái (legfeljebb [MAX_DEPTH] szint) listafájljai, legfeljebb [MAX_FILES] darab. */
    private fun listTree(treeUri: Uri): List<Source> {
        val result = mutableListOf<Source>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return result
        fun walk(parentId: String, depth: Int) {
            if (result.size >= MAX_FILES) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val folders = mutableListOf<String>()
            runCatching {
                resolver.query(children, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && result.size < MAX_FILES) {
                        val id: String? = cursor.getString(0)
                        val name = cursor.getString(1).orEmpty()
                        val mime = cursor.getString(2)
                        when {
                            id == null -> Unit
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id
                            PlaylistFormat.isPlaylistFile(name) -> result += Source(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                fileName = name,
                                dir = dirFromDocumentId(treeUri.authority, id),
                            )
                        }
                    }
                }
            }
            if (depth < MAX_DEPTH) folders.forEach { walk(it, depth + 1) }
        }
        walk(rootId, 0)
        return result
    }

    companion object {
        private const val MAX_DEPTH = 3
        private const val MAX_FILES = 300
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /** Elfogadott típusok a fájlválasztóban (a szolgáltatók eltérően jelölik a listafájlokat). */
        val PICKER_MIME_TYPES = arrayOf(
            "audio/x-mpegurl",
            "audio/mpegurl",
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "audio/x-scpls",
            "application/octet-stream",
            "text/plain",
        )

        /**
         * A listafájl mappája a tárhelyen belül a dokumentumazonosítóból: „primary:Music/Listák/a.m3u” →
         * „Music/Listák”. Más szolgáltatónál (pl. Letöltések) nem ismert.
         */
        fun dirFromDocumentId(authority: String?, documentId: String): String? = when {
            documentId.startsWith("raw:") ->
                PlaylistPaths.storageRelative(documentId.removePrefix("raw:"))?.substringBeforeLast('/', "")
            authority == EXTERNAL_STORAGE_AUTHORITY && documentId.contains(':') ->
                documentId.substringAfter(':').substringBeforeLast('/', "")
            else -> null
        }
    }
}
