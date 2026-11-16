package io.github.x1wer.szabadzene.data.playlists

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Egy exportálandó lista: a neve és a számai a lista sorrendjében. */
data class PlaylistExportSource(val name: String, val tracks: List<Track>)

/** Egy lista exportálásának eredménye: a létrehozott (vagy felülírt) fájl neve, vagy null hiba esetén. */
data class PlaylistExportOutcome(val playlistName: String, val fileName: String?, val trackCount: Int)

/** Az exportálás összesítője: a célmappa olvasható neve (ha ismert) és listánként az eredmény. */
data class PlaylistExportReport(val folder: String?, val outcomes: List<PlaylistExportOutcome>)

/**
 * Listák mentése .m3u8 fájlba a rendszer fájlválasztójával kiválasztott mappába (engedély és internet
 * nélkül). Alapból nem ír felül meglévő fájlt („Név (2).m3u8”); kérésre az azonos nevűt felülírja.
 */
class PlaylistExporter(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun export(
        treeUri: Uri,
        sources: List<PlaylistExportSource>,
        overwrite: Boolean,
    ): PlaylistExportReport = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val target = StorageLocation.fromDocumentId(treeUri.authority, rootId)
        // A mappa meglévő fájljai (név kisbetűvel → dokumentumazonosító); az ebben a körben írtakat nem írjuk felül.
        val existing = listChildren(treeUri, rootId).toMutableMap()
        val writtenNow = mutableSetOf<String>()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(PlaylistFileWriter.EXTENSION)
            ?: "application/octet-stream"

        val outcomes = sources.map { source ->
            val wanted = PlaylistFileWriter.fileNameFor(source.name)
            val fileName = runCatching {
                val existingId = existing[wanted.lowercase()]
                val (documentUri, name) = if (overwrite && existingId != null && wanted.lowercase() !in writtenNow) {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId) to wanted
                } else {
                    val unique = PlaylistFileWriter.uniqueFileName(wanted, existing.keys)
                    val created = DocumentsContract.createDocument(resolver, dirUri, mime, unique)
                        ?: throw IOException("createDocument")
                    // A szolgáltató átnevezheti (pl. ütközésnél): a valódi nevet olvassuk vissza.
                    val actual = displayName(created) ?: unique
                    existing[actual.lowercase()] = DocumentsContract.getDocumentId(created)
                    created to actual
                }
                val text = PlaylistFileWriter.m3u8(source.name, source.tracks, target)
                resolver.openOutputStream(documentUri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: throw IOException("openOutputStream")
                writtenNow += name.lowercase()
                name
            }.getOrNull()
            PlaylistExportOutcome(source.name, fileName, source.tracks.size)
        }
        PlaylistExportReport(folderLabel(target, dirUri), outcomes)
    }

    private fun listChildren(treeUri: Uri, parentId: String): Map<String, String> {
        val result = HashMap<String, String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        runCatching {
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id: String? = cursor.getString(0)
                    val name: String? = cursor.getString(1)
                    if (id != null && name != null) result[name.lowercase()] = id
                }
            }
        }
        return result
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** „Music/Listák” a belső tárhelyen, „1A2B-3C4D/Music” SD-kártyán, egyébként a mappa neve. */
    private fun folderLabel(target: StorageLocation?, dirUri: Uri): String? = when {
        target == null -> displayName(dirUri)
        target.volume == StorageLocation.PRIMARY -> target.path.ifEmpty { "/" }
        else -> "${target.volume}/${target.path}".trimEnd('/')
    }
}
