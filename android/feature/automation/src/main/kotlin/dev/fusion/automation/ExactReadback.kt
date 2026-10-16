package dev.fusion.automation
import java.io.InputStream
/** Compare bytes without allocating a second whole document. Caller owns stream and freezes expected during call.
 * Reads at most expected.size+1 bytes. No provider latency, honesty or physical durability guarantee.
 */
object ExactReadback {
    const val MAX_BYTES=2*1024*1024
    fun verify(input:InputStream,expected:ByteArray):Int {
        require(expected.size<=MAX_BYTES)
        var offset=0;val chunk=ByteArray(8192)
        while(offset<expected.size) {
            val n=input.read(chunk,0,minOf(chunk.size,expected.size-offset))
            check(n>0) {"Readback truncated or made no progress"}
            for(i in 0 until n) check(chunk[i]==expected[offset+i]) {"Readback differs"}
            offset+=n
        }
        check(input.read()==-1) {"Readback has trailing bytes"}
        return offset
    }
}
