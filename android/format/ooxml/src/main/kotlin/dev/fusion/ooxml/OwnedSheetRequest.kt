package dev.fusion.ooxml

import java.security.MessageDigest
import java.util.Collections

/** Correlates a bounded caller-owned model to a worker reply. NOT proof of honest computation.
 * Even a matching digest/keys can be forged by a compromised worker; numeric values remain
 * untrusted claims. No parent-side expression evaluation or arbitrary Office parsing occurs.
 */
class OwnedSheetRequest private constructor(private val encoded:ByteArray, private val keys:Set<String>) {
    val bytes:Int get()=encoded.size
    val sha256:String=MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") {"%02x".format(it)}
    fun frame():ByteArray=encoded.copyOf()
    fun accept(reportedBytes:Int,reportedDigest:String,result:ByteArray):Map<String,String> {
        require(reportedBytes==bytes && reportedDigest==sha256) {"Reply does not match owned request metadata"}
        val values=SheetFrame.readResult(result)
        require(values.keys==keys) {"Reply key set differs from owned request"}
        return Collections.unmodifiableMap(LinkedHashMap(values))
    }
    companion object {
        fun create(cells:Map<String,String>):OwnedSheetRequest {
            require(cells.size<=256)
            val copy=LinkedHashMap(cells) // Callers must not mutate concurrently during this copy.
            return OwnedSheetRequest(SheetFrame.encode(copy),Collections.unmodifiableSet(HashSet(copy.keys)))
        }
    }
}
