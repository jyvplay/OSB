// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Bounded PDF object ID allocator. Allocates PDF object IDs (object
 * number, generation number) pairs within bounded limits. IDs are unique
 * within the allocator; freed IDs can be reused. Generation numbers start
 * at 0 and increment on reuse. Does not write PDF — it only allocates IDs.
 *
 * Declared narrowings: at most [MAX_OBJECTS] live objects; object numbers
 * in [1, MAX_OBJ_NUM]; generation numbers in [0, MAX_GEN]; allocation
 * refuses when at capacity; freed IDs return to a reuse pool.
 */
class PdfObjectIdAllocator private constructor(
    private val live: MutableMap<Pair<Int, Int>, Boolean>,
    private val freeList: MutableList<Pair<Int, Int>>,
    private var nextObjNum: Int
) {
    companion object {
        const val MAX_OBJECTS = 100_000
        const val MAX_OBJ_NUM = 10_000_000
        const val MAX_GEN = 65535
    }

    fun allocate(objNum: Int, gen: Int): Pair<Int, Int> {
        require(objNum >= 1 && objNum <= MAX_OBJ_NUM) { "objNum out of bounds" }
        require(gen >= 0 && gen <= MAX_GEN) { "gen out of bounds" }
        require(live.size < MAX_OBJECTS || live.containsKey(Pair(objNum, gen))) {
            "too many live objects: $MAX_OBJECTS"
        }
        val id = Pair(objNum, gen)
        live[id] = true
        return id
    }

    fun allocateNext(): Pair<Int, Int> {
        require(live.size < MAX_OBJECTS) { "too many live objects: $MAX_OBJECTS" }
        // Reuse from free list first
        if (freeList.isNotEmpty()) {
            val id = freeList.removeAt(0)
            live[id] = true
            return id
        }
        // Otherwise assign next sequential object number
        while (nextObjNum <= MAX_OBJ_NUM) {
            val candidate = Pair(nextObjNum, 0)
            nextObjNum++
            if (!live.containsKey(candidate)) {
                live[candidate] = true
                return candidate
            }
        }
        throw IllegalStateException("object number space exhausted")
    }

    fun free(objNum: Int, gen: Int): Boolean {
        val id = Pair(objNum, gen)
        if (!live.containsKey(id)) return false
        live.remove(id)
        freeList.add(id)
        return true
    }

    fun isAllocated(objNum: Int, gen: Int): Boolean = live.containsKey(Pair(objNum, gen))

    fun liveCount(): Int = live.size
    fun freeCount(): Int = freeList.size
    fun clear() { live.clear(); freeList.clear(); nextObjNum = 1 }
}
