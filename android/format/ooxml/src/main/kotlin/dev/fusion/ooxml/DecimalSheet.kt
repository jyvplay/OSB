package dev.fusion.ooxml

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Original numeric dependency evaluator, not a LibreOffice formula implementation.
 * <=256 cells, <=512 UTF16/expression, dependency depth16, syntax depth16.
 * Literals or = arithmetic (+ - * / parentheses and A1..DX1000 references).
 * Decimal34 HALF_EVEN; bounded scale; no ranges, functions, strings, external names or macros.
 */
object DecimalSheet {
    private val mc=MathContext(34,RoundingMode.HALF_EVEN)
    private fun address(s:String) {
        require(s.matches(Regex("[A-Z]{1,2}[1-9][0-9]{0,3}")))
        val letters=s.takeWhile { it in 'A'..'Z' };var column=0
        for(c in letters) column=column*26+(c-'A'+1)
        require(column<=128 && s.drop(letters.length).toInt()<=1000)
    }
    private fun bound(n:BigDecimal):BigDecimal {
        val v=n.stripTrailingZeros();require(v.precision()<=34 && v.scale() in -100..100);return v
    }
    fun evaluate(input:Map<String,String>):Map<String,BigDecimal> {
        require(input.size<=256)
        val cells=input.toMap()
        for((key,value) in cells) { address(key);require(value.length in 1..512) }
        val done=linkedMapOf<String,BigDecimal>();val visiting=HashSet<String>();val heights=HashMap<String,Int>()
        fun cell(id:String,depth:Int):BigDecimal {
            require(depth<=16) { "Dependency depth" };done[id]?.let { return it }
            require(visiting.add(id)) { "Cell cycle" }
            val text=cells[id] ?: throw IllegalArgumentException("Unknown cell: $id")
            var height=0
            val result=if(text.startsWith('=')) Parser(text.drop(1)) { ref ->
                address(ref);val value=cell(ref,depth+1);height=maxOf(height,1+heights.getValue(ref))
                require(height<=16) { "Dependency height" };value
            }.parse()
                else { require(text.matches(Regex("[+-]?(0|[1-9][0-9]*)(\\.[0-9]+)?")));bound(BigDecimal(text)) }
            visiting.remove(id);heights[id]=height;done[id]=result;return result
        }
        for(id in cells.keys.sorted()) cell(id,0)
        return done.toSortedMap()
    }
    private class Parser(val s:String,val reference:(String)->BigDecimal) {
        var i=0
        fun space() { while(i<s.length && s[i]==' ') i++ }
        fun eat(c:Char):Boolean { space();if(i<s.length && s[i]==c) { i++;return true };return false }
        fun parse():BigDecimal { val v=expr(0);space();require(i==s.length) { "Trailing formula syntax" };return v }
        fun expr(depth:Int):BigDecimal {
            var n=term(depth)
            while(true) n=when { eat('+') -> bound(n.add(term(depth),mc));eat('-') -> bound(n.subtract(term(depth),mc));else -> return n }
        }
        fun term(depth:Int):BigDecimal {
            var n=atom(depth)
            while(true) n=when { eat('*') -> bound(n.multiply(atom(depth),mc));eat('/') -> {
                val divisor=atom(depth);require(divisor.signum()!=0) { "Division by zero" };bound(n.divide(divisor,mc))
            };else -> return n }
        }
        fun atom(depth:Int):BigDecimal {
            require(depth<=16) { "Syntax depth" };space()
            if(eat('+')) return atom(depth+1)
            if(eat('-')) return bound(atom(depth+1).negate())
            if(eat('(')) { val v=expr(depth+1);require(eat(')'));return v }
            val start=i
            if(i<s.length && s[i] in 'A'..'Z') {
                while(i<s.length && (s[i] in 'A'..'Z' || s[i] in '0'..'9')) i++
                return reference(s.substring(start,i))
            }
            while(i<s.length && s[i] in '0'..'9') i++
            require(i>start)
            if(i<s.length && s[i]=='.') { i++;val first=i;while(i<s.length && s[i] in '0'..'9') i++;require(i>first) }
            return bound(BigDecimal(s.substring(start,i)))
        }
    }
}
