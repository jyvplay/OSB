package dev.fusion.calc

import java.math.BigInteger

/** Deliberately separate parser/arithmetic from decimal and scientific evaluation. */
internal class ProgrammerParser(input: String) {
    private val t = Tokens(input)
    fun parse(): Long = expression(0,0).also { t.expect(TokenKind.END) }
    private fun expression(minimum: Int, depth: Int): Long {
        if(depth >= Calculator.MAX_DEPTH) t.fail("Nesting limit reached")
        val token=t.take()
        var left=when {
            token.kind==TokenKind.NUMBER -> {
                val text=token.text.lowercase()
                val base=when { text.startsWith("0x") -> 16; text.startsWith("0b") -> 2; text.startsWith("0o") -> 8; else -> 10 }
                val digits=if(base==10) text else text.drop(2)
                if(digits.isEmpty() || digits.any { it.digitToIntOrNull(base)==null }) t.fail("Invalid integer literal",token)
                val value=BigInteger(digits,base)
                if(value.bitLength()>64) t.fail("Literal exceeds 64 bits",token)
                value.toLong()
            }
            token.kind==TokenKind.LEFT -> expression(0,depth+1).also { t.expect(TokenKind.RIGHT) }
            token.kind==TokenKind.OP && token.text in listOf("-","+","~") -> expression(7,depth+1).let {
                when(token.text) { "-" -> -it; "~" -> it.inv(); else -> it }
            }
            else -> t.fail("Expected a 64-bit integer or parenthesis",token)
        }
        while(true) {
            val op=t.peek().text
            val precedence=when(op) { "|" -> 1; "^" -> 2; "&" -> 3; "<<",">>",">>>" -> 4; "+","-" -> 5; "*","/","%" -> 6; else -> -1 }
            if(precedence<minimum) return left
            t.take(); val right=expression(precedence+1,depth+1)
            left=when(op) {
                "|" -> left or right; "^" -> left xor right; "&" -> left and right
                "+" -> left+right; "-" -> left-right; "*" -> left*right
                "/" -> { if(right==0L) t.fail("Division by zero"); left/right }
                "%" -> { if(right==0L) t.fail("Remainder by zero"); left%right }
                else -> {
                    if(right !in 0..63) t.fail("Shift count must be from 0 to 63")
                    when(op) { "<<" -> left shl right.toInt(); ">>" -> left shr right.toInt(); else -> left ushr right.toInt() }
                }
            }
        }
    }
}
