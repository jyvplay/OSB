package dev.fusion.ooxml

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.math.BigDecimal
import java.util.Base64

/** Testable original arithmetic request/result framing. NOT an Office document or formula compatibility protocol. */
object SheetFrame {
    const val MAX_INPUT=256*1024
    const val MAX_OUTPUT=64*1024
    private fun address(key:String):Boolean {
        if(!key.matches(Regex("[A-Z]{1,2}[1-9][0-9]{0,3}"))) return false
        val letters=key.takeWhile { it in 'A'..'Z' };var column=0
        for(c in letters) column=column*26+(c-'A'+1)
        return column<=128 && key.drop(letters.length).toInt()<=1000
    }
    fun encode(cells:Map<String,String>):ByteArray {
        require(cells.size<=256)
        val text=buildString {
            append("FSC1\n")
            for((key,value) in cells.toSortedMap()) {
                require(address(key) && value.length in 1..512)
                val b=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                val bytes=ByteArray(b.remaining()).also(b::get)
                append(key).append('\t').append(Base64.getEncoder().encodeToString(bytes)).append('\n')
                require(length<=MAX_INPUT)
            }
        }
        return text.toByteArray(Charsets.US_ASCII)
    }
    fun decode(frame:ByteArray):Map<String,String> {
        require(frame.size in 5..MAX_INPUT && frame.all { it>=0 })
        val text=frame.toString(Charsets.US_ASCII);require(text.startsWith("FSC1\n") && text.endsWith('\n'))
        val lines=text.substring(5).dropLast(1).let { if(it.isEmpty()) emptyList() else it.split('\n') };require(lines.size<=256)
        val cells=linkedMapOf<String,String>()
        for(line in lines) {
            val p=line.split('\t');require(p.size==2 && p[0] !in cells && p[1].length<=2048)
            val raw=Base64.getDecoder().decode(p[1]);require(raw.size<=2048)
            cells[p[0]]=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
        }
        require(encode(cells).contentEquals(frame));return cells
    }
    fun execute(frame:ByteArray):ByteArray {
        val result=DecimalSheet.evaluate(decode(frame))
        return buildString { append("FSR1\n");for((key,value) in result) append(key).append('\t').append(value.toPlainString()).append('\n') }
            .toByteArray(Charsets.US_ASCII).also { require(it.size<=MAX_OUTPUT) }
    }
    fun readResult(frame:ByteArray):Map<String,String> {
        require(frame.size in 5..MAX_OUTPUT && frame.all { it>=0 })
        val text=frame.toString(Charsets.US_ASCII);require(text.startsWith("FSR1\n") && text.endsWith('\n'))
        val lines=text.substring(5).dropLast(1).let { if(it.isEmpty()) emptyList() else it.split('\n') };require(lines.size<=256)
        val result=linkedMapOf<String,String>()
        for(line in lines) {
            val p=line.split('\t');require(p.size==2 && address(p[0]) && p[0] !in result)
            require(p[1].length in 1..140 && p[1].matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")))
            val number=BigDecimal(p[1]).stripTrailingZeros();require(number.precision()<=34 && number.scale() in -100..100 && number.toPlainString()==p[1])
            result[p[0]]=p[1]
        }
        val canonical=buildString { append("FSR1\n");for((key,value) in result.toSortedMap()) append(key).append('\t').append(value).append('\n') }
        require(canonical==text);return result
    }
}
