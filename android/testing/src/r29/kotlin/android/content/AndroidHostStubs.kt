// SPDX-License-Identifier: Apache-2.0
package android.content

/**
 * TEST-ONLY host stubs. They exist so kotlinc-jvm can compile and execute the
 * production store logic that touches exactly two Android APIs
 * (Context.getSharedPreferences, SharedPreferences string-sets). Never
 * shipped, never on an Android classpath, NOT Android behavior evidence.
 */
open class Context {
    companion object {
        const val MODE_PRIVATE = 0
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        throw UnsupportedOperationException("host stub: no Android runtime")
}

interface SharedPreferences {
    fun getStringSet(key: String, def: MutableSet<String>?): MutableSet<String>?
    fun edit(): Editor

    interface Editor {
        fun putStringSet(key: String, values: MutableSet<String>?): Editor
        fun apply()
    }
}
