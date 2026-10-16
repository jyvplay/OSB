package dev.fusion.security

import java.net.URI
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Offline, explicit data minimization, NOT an anonymizer, URL opener, SSRF or tracking defense. */
object LinkDisclosure {
    const val MAX_UTF8=4096
    data class Projection(val text:String,val removedQuery:Boolean,val removedFragment:Boolean)
    fun project(raw:String):Projection {
        require(raw.length in 1..MAX_UTF8 && raw==raw.trim())
        require(raw.none {it.isISOControl() || it=='\\' || Character.getType(it)==Character.FORMAT.toInt()})
        val bytes=try {Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(raw))} catch(e:java.nio.charset.CharacterCodingException) {throw IllegalArgumentException("Malformed Unicode",e)}
        require(bytes.remaining()<=MAX_UTF8)
        val u=try {URI(raw)} catch(e:java.net.URISyntaxException) {throw IllegalArgumentException("Malformed URI",e)};val scheme=u.scheme?.lowercase(Locale.ROOT)
        require(scheme=="https" || scheme=="http") {"Explicit HTTP(S) only"}
        require(u.rawUserInfo==null && u.host!=null && !u.host.contains('%') && (u.port==-1 || u.port in 1..65535))
        val port=if(u.port==-1 || scheme=="https" && u.port==443 || scheme=="http" && u.port==80) "" else ":${u.port}"
        return Projection("$scheme://${u.host.lowercase(Locale.ROOT)}$port${u.rawPath.ifEmpty {"/"}}",u.rawQuery!=null,u.rawFragment!=null)
    }
}
