package io.github.x1wer.szabadzene.playback

/*
 * A várólista mentésének, visszaállításának és a kevert sorrend kezelésének tiszta logikája.
 * Nincs Android-függősége, ezért JVM-en tesztelhető.
 */

/** A mentett várólista. A [shuffleOrder] az alaplista indexeit tartalmazza lejátszási sorrendben. */
data class SavedQueue(
    val trackIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val shuffleOrder: List<Int>,
)

/**
 * Új várólista a most szóló szám körül („a mostani szám végigszól” mód): a [before] azonosítók az
 * aktuális szám elé, az [after] azonosítók mögé kerülnek az alaplistában. Keverésnél a [shuffleOrder]
 * az új alaplista indexeit adja lejátszási sorrendben, az aktuális számmal kezdve.
 */
data class KeepCurrentPlan(
    val before: List<Long>,
    val after: List<Long>,
    val shuffleOrder: List<Int>?,
) {
    val currentIndex: Int get() = before.size
    val size: Int get() = before.size + 1 + after.size
}

/** A visszaállítandó várólista: csak a még elérhető számokkal. */
data class RestoredQueue<T>(
    val items: List<T>,
    val startIndex: Int,
    val startPositionMs: Long,
    /** Kevert sorrend az új (szűrt) indexekkel, vagy null, ha nem volt érvényes. */
    val shuffleOrder: List<Int>?,
)

object QueueLogic {

    /**
     * A mentett várólistából kihagyja a már nem elérhető számokat, és ehhez igazítja az aktuális
     * számot, a pozíciót és a kevert sorrendet. Ha az aktuális szám tűnt el, a következő elérhető
     * számmal folytat (az elejéről); ha egy sem maradt, null.
     */
    fun <T> restore(saved: SavedQueue, available: Map<Long, T>): RestoredQueue<T>? {
        val keptOld = saved.trackIds.indices.filter { saved.trackIds[it] in available }
        if (keptOld.isEmpty()) return null
        val newIndexOfOld = HashMap<Int, Int>(keptOld.size)
        keptOld.forEachIndexed { newIndex, oldIndex -> newIndexOfOld[oldIndex] = newIndex }
        val items = keptOld.map { available.getValue(saved.trackIds[it]) }

        // A kevert sorrendet akkor is megőrizzük, ha a keverés épp ki van kapcsolva:
        // visszakapcsoláskor így ugyanaz a sorrend folytatódik.
        val validOrder = saved.shuffleOrder.size == saved.trackIds.size &&
            saved.shuffleOrder.toSet() == saved.trackIds.indices.toSet()
        val newOrder = if (validOrder) saved.shuffleOrder.mapNotNull { newIndexOfOld[it] } else null

        val current = saved.currentIndex
        val (startIndex, position) = when {
            newIndexOfOld.containsKey(current) -> newIndexOfOld.getValue(current) to saved.positionMs.coerceAtLeast(0L)
            else -> {
                // A következő elérhető szám a lejátszási sorrendben (kevert vagy alap).
                val playOrderOld = if (saved.shuffleEnabled && validOrder) {
                    saved.shuffleOrder
                } else {
                    saved.trackIds.indices.toList()
                }
                val at = playOrderOld.indexOf(current)
                val next = if (at >= 0) {
                    playOrderOld.drop(at + 1).firstOrNull { newIndexOfOld.containsKey(it) }
                } else {
                    null
                }
                val first = playOrderOld.first { newIndexOfOld.containsKey(it) }
                newIndexOfOld.getValue(next ?: first) to 0L
            }
        }
        return RestoredQueue(items, startIndex, position, newOrder)
    }

    /**
     * Kevert sorrend „Lejátszás következőként” után: a frissen beszúrt indexek közvetlenül az aktuális
     * szám után kerülnek (a beszúrás sorrendjében), a többi elem sorrendje nem változik.
     */
    fun playNextOrder(order: List<Int>, currentIndex: Int, inserted: List<Int>): List<Int> {
        val insertedSet = inserted.toSet()
        val rest = order.filterNot { it in insertedSet }.toMutableList()
        val at = rest.indexOf(currentIndex)
        rest.addAll(if (at >= 0) at + 1 else 0, inserted)
        return rest
    }

    /** Kevert sorrend „Hozzáadás a várólistához” után: a hozzáfűzött elemek a sor végére kerülnek. */
    fun appendOrder(order: List<Int>, inserted: List<Int>): List<Int> {
        val insertedSet = inserted.toSet()
        return order.filterNot { it in insertedSet } + inserted
    }

    /**
     * A „mostani szám végigszól” mód: az [ids] lista (a zenetár sorrendjében) lesz az új várólista úgy,
     * hogy a most szóló szám a helyén marad. Ha a szám benne van a listában, sorrendben utána a lista
     * következő száma jön; ha nincs benne, a lista elejével folytatódik. Keverésnél a mostani szám után
     * a többi szám véletlen sorrendben jön.
     */
    fun keepCurrentPlan(ids: List<Long>, currentId: Long?, shuffle: Boolean, random: kotlin.random.Random): KeepCurrentPlan {
        val at = if (currentId == null) -1 else ids.indexOf(currentId)
        val before = if (at >= 0) ids.subList(0, at).toList() else emptyList()
        val after = if (at >= 0) ids.subList(at + 1, ids.size).filter { it != currentId } else ids.filter { it != currentId }
        val plan = KeepCurrentPlan(before, after, shuffleOrder = null)
        if (!shuffle) return plan
        val others = (0 until plan.size).filter { it != plan.currentIndex }.shuffled(random)
        return plan.copy(shuffleOrder = listOf(plan.currentIndex) + others)
    }

    fun encodeOrder(order: List<Int>): String = order.joinToString(",")

    fun decodeOrder(text: String): List<Int> =
        if (text.isBlank()) emptyList() else text.split(',').mapNotNull { it.trim().toIntOrNull() }

    /**
     * Egy szám minden előfordulásának indexe a várólistában, csökkenő sorrendben – így
     * egymás után eltávolíthatók anélkül, hogy a még törlendők indexe elcsúszna.
     */
    fun indicesToRemove(mediaIds: List<String>, trackId: Long): List<Int> {
        val id = trackId.toString()
        return mediaIds.indices.filter { mediaIds[it] == id }.sortedDescending()
    }
}
