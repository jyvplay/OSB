package dev.fusion.automation
import java.io.ByteArrayOutputStream
import java.io.InputStream
/** Bounded capture only, no URI/filename or XML parsing. May block on provider IO; not a hard deadline. */
object WorksheetInput {
 const val MAX_BYTES=2*1024*1024
 fun capture(input:InputStream):ByteArray {
  val out=ByteArrayOutputStream();val chunk=ByteArray(8192)
  while(true) {
   val n=input.read(chunk,0,minOf(chunk.size,MAX_BYTES-out.size()+1))
   if(n<0) return out.toByteArray()
   require(n>0) {"Provider made no progress"};require(out.size()+n<=MAX_BYTES) {"Worksheet exceeds 2MiB"}
   out.write(chunk,0,n)
  }
 }
}
