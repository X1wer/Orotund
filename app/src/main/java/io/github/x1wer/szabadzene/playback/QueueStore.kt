package io.github.x1wer.szabadzene.playback

import io.github.x1wer.szabadzene.data.db.PlaybackStateEntity
import io.github.x1wer.szabadzene.data.db.QueueDao

/** A várólista és a lejátszási állapot tartós tárolása a helyi adatbázisban. */
class QueueStore(
    private val dao: QueueDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Null, ha még nincs mentett, nem üres várólista. */
    suspend fun load(): SavedQueue? {
        val state = dao.state() ?: return null
        val items = dao.items()
        if (items.isEmpty()) return null
        return SavedQueue(
            trackIds = items.map { it.trackId },
            currentIndex = state.currentIndex,
            positionMs = state.positionMs,
            repeatMode = state.repeatMode,
            shuffleEnabled = state.shuffleEnabled,
            shuffleOrder = QueueLogic.decodeOrder(state.shuffleOrder),
        )
    }

    suspend fun save(queue: SavedQueue) {
        dao.saveAll(
            trackIds = queue.trackIds,
            state = PlaybackStateEntity(
                currentIndex = queue.currentIndex,
                positionMs = queue.positionMs,
                repeatMode = queue.repeatMode,
                shuffleEnabled = queue.shuffleEnabled,
                shuffleOrder = QueueLogic.encodeOrder(queue.shuffleOrder),
                updatedAt = clock(),
            ),
        )
    }
}
