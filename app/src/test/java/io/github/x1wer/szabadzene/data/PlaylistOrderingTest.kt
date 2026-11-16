package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.playlists.PlaylistListEntry
import io.github.x1wer.szabadzene.data.playlists.PlaylistListSettings
import io.github.x1wer.szabadzene.data.playlists.PlaylistOrdering
import io.github.x1wer.szabadzene.data.playlists.PlaylistSortField
import org.junit.Assert.assertEquals
import org.junit.Test

/** A Listák fül sorrendje: rendezési mezők, irány, a különleges listák helye. */
class PlaylistOrderingTest {
    private val special = listOf(
        PlaylistListEntry(-1, "Kedvencek", 79, createdAt = 0, specialRank = 0),
        PlaylistListEntry(-2, "Legtöbbet játszott", 500, createdAt = 0, specialRank = 1),
        PlaylistListEntry(-3, "Nemrég játszott", 5, createdAt = 0, specialRank = 2),
    )
    private val own = listOf(
        PlaylistListEntry(10, "Énekes", 28, createdAt = 300),
        PlaylistListEntry(11, "alvás", 14, createdAt = 100),
        PlaylistListEntry(12, "Zúzás", 3, createdAt = 200),
    )

    private fun keys(settings: PlaylistListSettings) = PlaylistOrdering.order(own + special, settings).map { it.key }

    @Test
    fun specialListsStayFirstByDefault() {
        assertEquals(listOf(-1L, -2L, -3L, 11L, 10L, 12L), keys(PlaylistListSettings()))
        assertEquals(listOf(-1L, -2L, -3L, 12L, 10L, 11L), keys(PlaylistListSettings(descending = true)))
    }

    @Test
    fun sortsByCreationAndSize() {
        assertEquals(listOf(-1L, -2L, -3L, 11L, 12L, 10L), keys(PlaylistListSettings(PlaylistSortField.CREATED)))
        assertEquals(listOf(-1L, -2L, -3L, 10L, 11L, 12L), keys(PlaylistListSettings(PlaylistSortField.SIZE, descending = true)))
    }

    @Test
    fun specialListsMixInWhenNotPinned() {
        // Név szerint (ékezet- és kisbetű-függetlenül): alvás, Énekes, Kedvencek, Legtöbbet…, Nemrég…, Zúzás
        assertEquals(listOf(11L, 10L, -1L, -2L, -3L, 12L), keys(PlaylistListSettings(specialFirst = false)))
        // Számok száma szerint csökkenőben a Legtöbbet játszott kerül előre.
        assertEquals(listOf(-2L, -1L, 10L, 11L, -3L, 12L), keys(PlaylistListSettings(PlaylistSortField.SIZE, true, specialFirst = false)))
        // Létrehozás szerint a különlegesek a legrégebbiek, a saját sorrendjükben.
        assertEquals(listOf(-1L, -2L, -3L, 11L, 12L, 10L), keys(PlaylistListSettings(PlaylistSortField.CREATED, specialFirst = false)))
    }
}
