package dev.fusion.settingsui

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class PreferenceDelta(val key:String,val before:String?,val after:String?)
class PreferenceConflict(val keys:List<String>):IllegalStateException("Settings conflicts: "+keys.joinToString(","))
/** Three-way immutable patch, NOT storage, authentication, a permission grant or arbitrary-setting enablement. */
class SettingsPatch private constructor(private val changes:List<PreferenceDelta>) {
    fun preview():List<PreferenceDelta> = changes.toList()
    fun apply(current:Map<String,String>):Map<String,String> {
        val copy=checked(current)
        val conflicts=changes.filter { copy[it.key]!=it.before && copy[it.key]!=it.after }.map { it.key }
        if(conflicts.isNotEmpty()) throw PreferenceConflict(conflicts)
        val result=copy.toMutableMap()
        for(c in changes) if(c.after==null) result.remove(c.key) else result[c.key]=c.after
        return checked(result)
    }
    companion object {
        fun plan(base:Map<String,String>,desired:Map<String,String>):SettingsPatch {
            val a=checked(base);val b=checked(desired)
            return SettingsPatch((a.keys+b.keys).sorted().filter { a[it]!=b[it] }.map { PreferenceDelta(it,a[it],b[it]) })
        }
        private fun checked(input:Map<String,String>):Map<String,String> {
            require(input.size<=64);var units=0
            for((key,value) in input) {
                require(key.matches(Regex("[a-z][a-z0-9_.-]{0,63}")) && value.length<=1024)
                Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                units+=key.length+value.length;require(units<=65536)
            }
            return input.toMap()
        }
    }
}
