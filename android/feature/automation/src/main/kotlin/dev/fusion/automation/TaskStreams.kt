package dev.fusion.automation

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded stream read; task input defaults to128KiB, generated report readback may use512KiB.
 * Consumes at most limit+1 bytes; caller owns stream. No wall-clock/provider latency bound. */
object TaskStreams {
    fun bytes(input:InputStream,maxBytes:Int=TaskFrame.MAX_BYTES):ByteArray {
        require(maxBytes in 1..512*1024)
        val output=ByteArrayOutputStream();val chunk=ByteArray(8192)
        while(true) {
            val n=input.read(chunk,0,minOf(chunk.size,maxBytes-output.size()+1))
            if(n<0) break
            require(n>0) { "No progress reading task data" }
            require(output.size()+n<=maxBytes) { "Task transfer exceeds byte budget" }
            output.write(chunk,0,n)
        }
        return output.toByteArray()
    }
    fun read(input:InputStream)=TaskFrame.decode(bytes(input))
}
