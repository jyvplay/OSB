package dev.fusion.settings

/**
 * Settings-key rename migration (old key -> new key maps applied across
 * app versions).
 *
 * Rules enforced: renames resolve TRANSITIVELY (a->b plus b->c moves a
 * to c); cycles refuse at map validation (a->b, b->a has no fixed
 * point); two different old keys may NOT land on the same live new key
 * when both carry values (silent last-writer-wins would destroy user
 * settings — refusal forces an explicit merge decision); applying the
 * same migration twice is a no-op (idempotence is executed, not
 * assumed).
 *
 * Declared narrowings: key NAMES only — value transformation
 * (schema/unit changes) is a different migration shape and refuses by
 * absence here.
 */
object KeyMigrate {
    const val SCOPE = "Key renames with transitive resolution, cycle refusal and collision refusal; values untouched."

    fun validate(renames: Map<String, String>): Map<String, String> {
        for ((old, new) in renames) {
            require(old.isNotEmpty() && new.isNotEmpty()) { "empty key in rename map" }
            require(old != new) { "self-rename '$old'" }
        }
        // cycle detection by chain walking with a visited set per start
        for (start in renames.keys) {
            var cur = start
            val seen = HashSet<String>()
            while (renames.containsKey(cur)) {
                require(seen.add(cur)) { "rename cycle through '$cur'" }
                cur = renames.getValue(cur)
            }
        }
        return renames
    }

    /** Final destination of a key under the (validated) map. */
    fun resolve(renames: Map<String, String>, key: String): String {
        var cur = key
        var hops = 0
        while (renames.containsKey(cur)) {
            cur = renames.getValue(cur)
            hops++
            require(hops <= renames.size) { "unvalidated cyclic map" }
        }
        return cur
    }

    fun apply(renames: Map<String, String>, settings: Map<String, String>): Map<String, String> {
        validate(renames)
        val out = LinkedHashMap<String, String>()
        for ((key, value) in settings) {
            val dest = resolve(renames, key)
            if (out.containsKey(dest)) {
                require(out[dest] == value) {
                    "keys collide on '$dest' with different values — refusing silent last-writer-wins"
                }
            } else out[dest] = value
        }
        return out
    }
}
