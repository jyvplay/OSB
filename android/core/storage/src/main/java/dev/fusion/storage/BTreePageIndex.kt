// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Clean-room in-memory order-$M$ B+Tree sorted key/value & range-scan index (`BTreePageIndex`).
 *
 * Powers `:core:storage` local document metadata catalogs and range-filtered queries:
 * - All values reside in linked leaf pages (`LeafNode.next`) for $O(\log N + K)$ ordered `rangeScan`.
 * - Internal pages hold separator keys guiding logarithmic descent (`get`, `put`, `remove`).
 * - Includes explicit `verifyInvariants()` checking balanced leaf depth, sorted order, internal separator
 *   bounds, and leaf-chain completeness against `size`.
 */
class BTreePageIndex<K : Comparable<K>, V>(
    val maxKeysPerNode: Int = 8,
) {
    companion object {
        const val SCOPE =
            "Generic order-M B+Tree index (O(log N) get/put/remove, O(log N + K) linked-leaf rangeScan, structural invariant verifier); WAL crash recovery out of scope."
    }

    init {
        require(maxKeysPerNode in 3..256) { "maxKeysPerNode must be in 3..256: $maxKeysPerNode" }
    }

    private sealed class Node<K : Comparable<K>, V> {
        val keys = ArrayList<K>()
    }

    private class InternalNode<K : Comparable<K>, V> : Node<K, V>() {
        val children = ArrayList<Node<K, V>>()
    }

    private class LeafNode<K : Comparable<K>, V> : Node<K, V>() {
        val values = ArrayList<V>()
        var next: LeafNode<K, V>? = null
    }

    private data class Split<K : Comparable<K>, V>(val promotedKey: K, val rightNode: Node<K, V>)

    private var root: Node<K, V> = LeafNode()
    var size: Int = 0
        private set

    fun get(key: K): V? {
        val leaf = findLeaf(root, key)
        val idx = leaf.keys.binarySearch(key)
        return if (idx >= 0) leaf.values[idx] else null
    }

    fun put(key: K, value: V): V? {
        var oldVal: V? = null
        fun insertRec(node: Node<K, V>): Split<K, V>? = when (node) {
            is LeafNode -> {
                val idx = node.keys.binarySearch(key)
                if (idx >= 0) {
                    oldVal = node.values[idx]
                    node.values[idx] = value
                    null
                } else {
                    val ins = -idx - 1
                    node.keys.add(ins, key)
                    node.values.add(ins, value)
                    size++
                    if (node.keys.size > maxKeysPerNode) splitLeaf(node) else null
                }
            }
            is InternalNode -> {
                val childIdx = childIndexFor(node.keys, key)
                val childSplit = insertRec(node.children[childIdx])
                if (childSplit == null) {
                    null
                } else {
                    node.keys.add(childIdx, childSplit.promotedKey)
                    node.children.add(childIdx + 1, childSplit.rightNode)
                    if (node.keys.size > maxKeysPerNode) splitInternal(node) else null
                }
            }
        }

        val rootSplit = insertRec(root)
        if (rootSplit != null) {
            val newRoot = InternalNode<K, V>()
            newRoot.keys.add(rootSplit.promotedKey)
            newRoot.children.add(root)
            newRoot.children.add(rootSplit.rightNode)
            root = newRoot
        }
        return oldVal
    }

    fun remove(key: K): V? {
        val leaf = findLeaf(root, key)
        val idx = leaf.keys.binarySearch(key)
        if (idx < 0) return null
        val removed = leaf.values.removeAt(idx)
        leaf.keys.removeAt(idx)
        size--
        return removed
    }

    fun rangeScan(fromInclusive: K, toInclusive: K): List<Pair<K, V>> {
        if (fromInclusive > toInclusive) return emptyList()
        val out = ArrayList<Pair<K, V>>()
        var leaf: LeafNode<K, V>? = findLeaf(root, fromInclusive)
        while (leaf != null) {
            for (i in leaf.keys.indices) {
                val k = leaf.keys[i]
                if (k < fromInclusive) continue
                if (k > toInclusive) return out
                out.add(k to leaf.values[i])
            }
            leaf = leaf.next
        }
        return out
    }

    fun verifyInvariants(): Boolean {
        val leafDepths = mutableSetOf<Int>()
        fun checkNode(node: Node<K, V>, depth: Int, loInclusive: K?, hiExclusive: K?) {
            for (i in 1 until node.keys.size) {
                check(node.keys[i - 1] < node.keys[i]) { "Unsorted keys in B+Tree node" }
            }
            for (k in node.keys) {
                if (loInclusive != null) check(k >= loInclusive) { "Key $k violates lower separator $loInclusive" }
                if (hiExclusive != null) check(k < hiExclusive) { "Key $k violates upper separator $hiExclusive" }
            }
            when (node) {
                is LeafNode -> leafDepths.add(depth)
                is InternalNode -> {
                    check(node.children.size == node.keys.size + 1) { "Internal child arity mismatch" }
                    for (cIdx in node.children.indices) {
                        val childLo = if (cIdx == 0) loInclusive else node.keys[cIdx - 1]
                        val childHi = if (cIdx == node.keys.size) hiExclusive else node.keys[cIdx]
                        checkNode(node.children[cIdx], depth + 1, childLo, childHi)
                    }
                }
            }
        }
        checkNode(root, 0, null, null)
        check(leafDepths.size == 1) { "Unbalanced leaf depths: $leafDepths" }

        // Walk linked-leaf chain from leftmost leaf and verify total count + global strict ordering.
        var cur: Node<K, V> = root
        while (cur is InternalNode) cur = cur.children.first()
        var leaf: LeafNode<K, V>? = cur as LeafNode<K, V>
        var seen = 0
        var prevKey: K? = null
        while (leaf != null) {
            for (k in leaf.keys) {
                if (prevKey != null) check(prevKey < k) { "Leaf chain out of order: $prevKey >= $k" }
                prevKey = k
                seen++
            }
            leaf = leaf.next
        }
        check(seen == size) { "Leaf chain count $seen != size $size" }
        return true
    }

    private fun findLeaf(start: Node<K, V>, key: K): LeafNode<K, V> {
        var cur = start
        while (cur is InternalNode) {
            cur = cur.children[childIndexFor(cur.keys, key)]
        }
        return cur as LeafNode<K, V>
    }

    private fun childIndexFor(keys: List<K>, key: K): Int {
        var i = 0
        while (i < keys.size && key >= keys[i]) i++
        return i
    }

    private fun splitLeaf(left: LeafNode<K, V>): Split<K, V> {
        val mid = left.keys.size / 2
        val right = LeafNode<K, V>()
        right.keys.addAll(left.keys.subList(mid, left.keys.size))
        right.values.addAll(left.values.subList(mid, left.values.size))
        left.keys.subList(mid, left.keys.size).clear()
        left.values.subList(mid, left.values.size).clear()
        right.next = left.next
        left.next = right
        return Split(right.keys.first(), right)
    }

    private fun splitInternal(left: InternalNode<K, V>): Split<K, V> {
        val mid = left.keys.size / 2
        val promoted = left.keys[mid]
        val right = InternalNode<K, V>()
        right.keys.addAll(left.keys.subList(mid + 1, left.keys.size))
        right.children.addAll(left.children.subList(mid + 1, left.children.size))
        left.keys.subList(mid, left.keys.size).clear()
        left.children.subList(mid + 1, left.children.size).clear()
        return Split(promoted, right)
    }
}
