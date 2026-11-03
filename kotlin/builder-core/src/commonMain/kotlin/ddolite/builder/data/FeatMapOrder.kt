package ddolite.builder.data

import ddolite.builder.model.FeatMapEntry

/** Which name the feat map is sorted by. Ported from `PlannerEnum` (basEnum.bas:266). */
enum class FeatMapKey { UNKNOWN, LITE, RON, BUILDER }

/**
 * The feat map in VB's working order: `db.FeatMap` is sorted in place by whichever planner's names
 * the last import or export searched (`db.FeatMapIndex`), starting in file order. Ported from
 * SortFeatMap (basFormat.bas:375), SeekFeatMap (:408) and CompareFeatMap (:435).
 *
 * The shellsort isn't stable, so where two lines share a name (DDO Builder's "Half-Elf Dilettante:
 * Wizard" is on two lines) which one a search finds depends on the order the map was in before, as
 * in VB. One per data load, like VB's global.
 */
class FeatMapOrder(entries: List<FeatMapEntry>) {
    private val map = ArrayList(entries)

    var key = FeatMapKey.UNKNOWN
        private set

    private fun name(e: FeatMapEntry): String = when (key) {
        FeatMapKey.LITE -> e.lite
        FeatMapKey.RON -> e.ron
        FeatMapKey.BUILDER -> e.builder
        FeatMapKey.UNKNOWN -> ""
    }

    /** SortFeatMap: VB's shellsort (1-based there) by [by]'s names; nothing to do if already in that order. */
    fun sort(by: FeatMapKey) {
        if (key == by) {
            return
        }
        key = by
        if (key == FeatMapKey.UNKNOWN) {
            return
        }
        // VB's array is 1-based: its position p is map[p - 1] here.
        val min = 1
        val max = map.size
        var gap = min
        do {
            gap = 3 * gap + 1
        } while (gap <= max)
        do {
            gap /= 3
            for (i in gap + min..max) {
                val swap = map[i - 1]
                var hold = i
                while (name(map[hold - gap - 1]) > name(swap)) {
                    map[hold - 1] = map[hold - gap - 1]
                    hold -= gap
                    if (hold < min + gap) {
                        break
                    }
                }
                map[hold - 1] = swap
            }
        } while (gap != 1)
    }

    /** SeekFeatMap: a binary search by the current key's names; null if not found (VB's 0). */
    fun seek(display: String): FeatMapEntry? {
        if (key == FeatMapKey.UNKNOWN) {
            return null
        }
        var first = 0
        var last = map.size - 1
        while (first <= last) {
            val mid = (first + last) / 2
            val c = name(map[mid]).compareTo(display)
            if (c < 0) {
                first = mid + 1
            } else if (c > 0) {
                last = mid - 1
            } else {
                return map[mid]
            }
        }
        return null
    }
}
