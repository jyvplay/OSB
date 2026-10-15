package dev.fusion.model

import java.net.URI

data class LaunchTarget(val module:String,val workspace:Int) {
    init { require(module in LaunchLinks.modules && workspace in 1..3) }
}
/** Canonical own-app addressing only. Not registered as an Android Intent handler or permission grant. */
object LaunchLinks {
    internal val modules=setOf("files","editor","notes","calculator","browser","settings")
    fun encode(target:LaunchTarget):String="fusion://open/${target.module}?workspace=${target.workspace}"
    fun decode(text:String):LaunchTarget {
        require(text.length in 1..128)
        val uri=try { URI(text) } catch(e:java.net.URISyntaxException) { throw IllegalArgumentException("Invalid launch link",e) }
        require(uri.scheme=="fusion" && uri.rawAuthority=="open" && uri.rawFragment==null)
        val module=uri.rawPath.removePrefix("/")
        val query=uri.rawQuery ?: throw IllegalArgumentException("Missing workspace")
        require(query.matches(Regex("workspace=[1-3]")))
        return LaunchTarget(module,query.last()-'0').also { require(encode(it)==text) { "Noncanonical link" } }
    }
}
