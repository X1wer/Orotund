package io.github.x1wer.szabadzene.data.playlists

import io.github.x1wer.szabadzene.data.library.searchKey

/** A Listák fül rendezése: név, létrehozás ideje vagy számok száma szerint. */
enum class PlaylistSortField { NAME, CREATED, SIZE }

/**
 * A Listák fül sorrendje. [specialFirst]: a három különleges lista (Kedvencek, Legtöbbet játszott, Nemrég
 * játszott) mindig elöl áll, a saját sorrendjében; ha ki van kapcsolva, a többi listával együtt rendeződik.
 */
data class PlaylistListSettings(
    val field: PlaylistSortField = PlaylistSortField.NAME,
    val descending: Boolean = false,
    val specialFirst: Boolean = true,
)

/** Egy sor a Listák fülön. A különleges listák [createdAt] értéke 0 (ők a „legrégebbiek”), [specialRank] a rögzített sorrend. */
data class PlaylistListEntry(
    val key: Long,
    val name: String,
    val size: Int,
    val createdAt: Long,
    val specialRank: Int? = null,
)

object PlaylistOrdering {
    /** A sorok a beállítás szerint. Egyenlőségnél név, majd azonosító dönt, így a sorrend stabil. */
    fun order(entries: List<PlaylistListEntry>, settings: PlaylistListSettings): List<PlaylistListEntry> {
        val byName = compareBy<PlaylistListEntry> { searchKey(it.name) }.thenBy { it.key }
        val primary: Comparator<PlaylistListEntry> = when (settings.field) {
            PlaylistSortField.NAME -> compareBy { searchKey(it.name) }
            PlaylistSortField.CREATED -> compareBy<PlaylistListEntry> { it.createdAt }.thenBy { it.specialRank ?: Int.MAX_VALUE }
            PlaylistSortField.SIZE -> compareBy { it.size }
        }
        val sorted = (if (settings.descending) primary.reversed() else primary).then(byName)
        if (!settings.specialFirst) return entries.sortedWith(sorted)
        val (special, own) = entries.partition { it.specialRank != null }
        return special.sortedBy { it.specialRank } + own.sortedWith(sorted)
    }
}
