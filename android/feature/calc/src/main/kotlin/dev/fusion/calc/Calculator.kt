package dev.fusion.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/** PR-09: bounded expressions; no eval, JS, network, reflection, or implicit unit/radix changes. */
enum class CalculatorMode { DECIMAL, SCIENTIFIC, PROGRAMMER, CONVERT }
enum class AngleUnit { RADIANS, DEGREES }
enum class OutputRadix(val base: Int) { DECIMAL(10), HEX(16), BINARY(2), OCTAL(8) }
data class CalculationRequest(
    val mode: CalculatorMode = CalculatorMode.DECIMAL,
    val expression: String = "",
    val angle: AngleUnit = AngleUnit.RADIANS,
    val radix: OutputRadix = OutputRadix.DECIMAL,
    val fromUnit: String = "m",
    val toUnit: String = "cm",
)
data class Calculation(val request: CalculationRequest, val value: String, val explanation: String)
class CalculationException(message: String) : IllegalArgumentException(message)

object Calculator {
    const val MAX_INPUT = 512
    const val MAX_TOKENS = 256
    const val MAX_DEPTH = 32
    val context = MathContext(34, RoundingMode.HALF_EVEN)

    fun calculate(request: CalculationRequest): Result<Calculation> = try {
        requireInput(request.expression)
        Units.definition(request.fromUnit); Units.definition(request.toUnit)
        Result.success(when (request.mode) {
            CalculatorMode.DECIMAL -> Calculation(request, decimal(DecimalParser(request.expression).parse()),
                "Decimal: 34 significant digits, half-even rounding. % is remainder; no implied percentages.")
            CalculatorMode.SCIENTIFIC -> {
                val value = ScientificParser(request.expression, request.angle).parse()
                Calculation(request, if (value == 0.0) "0" else value.toString(),
                    "Scientific: approximate binary64; angles ${request.angle.name.lowercase(Locale.ROOT)}. ^ is power; ! is factorial.")
            }
            CalculatorMode.PROGRAMMER -> {
                val bits = ProgrammerParser(request.expression).parse()
                val value = when (request.radix) {
                    OutputRadix.DECIMAL -> bits.toString()
                    OutputRadix.HEX -> "0x" + java.lang.Long.toUnsignedString(bits, 16).uppercase(Locale.ROOT).padStart(16, '0')
                    OutputRadix.BINARY -> "0b" + java.lang.Long.toUnsignedString(bits, 2).padStart(64, '0')
                    OutputRadix.OCTAL -> "0o" + java.lang.Long.toUnsignedString(bits, 8).padStart(22, '0')
                }
                Calculation(request, value, "64-bit two's complement; arithmetic wraps. ^ is XOR. >> arithmetic, >>> logical; shifts 0–63. Input prefixes 0x/0b/0o, otherwise decimal.")
            }
            CalculatorMode.CONVERT -> {
                val converted = Units.convert(DecimalParser(request.expression).parse(), request.fromUnit, request.toUnit)
                Calculation(request, "${decimal(converted)} ${request.toUnit}",
                    "${request.fromUnit} → ${request.toUnit}; dimension checked; decimal34 half-even. No currency/calendar conversion.")
            }
        })
    } catch(error: IllegalArgumentException) { Result.failure(error)
    } catch(error: ArithmeticException) { Result.failure(error) }
    // VM cancellation and fatal runtime errors are not converted into calculation results.
    internal fun requireInput(input: String) {
        if (input.isBlank()) throw CalculationException("Enter an expression.")
        if (input.length > MAX_INPUT) throw CalculationException("Expression limit: $MAX_INPUT characters.")
    }
    internal fun decimal(value: BigDecimal): String {
        bounded(value)
        val normalized = value.stripTrailingZeros()
        return if (normalized.signum() == 0) "0" else if (normalized.scale() in -20..34) normalized.toPlainString() else normalized.toString()
    }
    internal fun bounded(value: BigDecimal): BigDecimal {
        val adjustedExponent = value.precision().toLong() - value.scale() - 1
        if (value.signum() != 0 && adjustedExponent !in -1000..1000) throw CalculationException("Decimal adjusted exponent must be between -1000 and 1000.")
        return value
    }
}

internal enum class TokenKind { NUMBER, NAME, OP, LEFT, RIGHT, COMMA, END }
internal data class Token(val kind: TokenKind, val text: String, val position: Int)

/** One tokenizer with explicit, mode-independent tokens. Each evaluator admits only its grammar. */
internal class Tokens(input: String) {
    private val tokens = buildList {
        Calculator.requireInput(input)
        var i = 0
        while (i < input.length) {
            if (input[i].isWhitespace()) { i++; continue }
            if (size >= Calculator.MAX_TOKENS) throw CalculationException("Too many tokens (limit ${Calculator.MAX_TOKENS}).")
            val start = i
            val c = input[i]
            when {
                c in '0'..'9' || c == '.' -> {
                    if (c == '0' && i + 1 < input.length && input[i+1].lowercaseChar() in listOf('x','b','o')) {
                        i += 2
                        while (i < input.length && (input[i] in '0'..'9' || input[i].lowercaseChar() in 'a'..'f')) i++
                    } else {
                        while (i < input.length && input[i] in '0'..'9') i++
                        if (i < input.length && input[i] == '.') { i++; while (i < input.length && input[i] in '0'..'9') i++ }
                        if (i < input.length && input[i].lowercaseChar() == 'e') {
                            i++
                            if (i < input.length && input[i] in "+-") i++
                            while (i < input.length && input[i] in '0'..'9') i++
                        }
                    }
                    val number = input.substring(start,i)
                    if (number.length > 128) throw CalculationException("Number limit: 128 characters.")
                    add(Token(TokenKind.NUMBER,number,start))
                }
                c in 'a'..'z' || c in 'A'..'Z' || c == 'π' -> {
                    i++
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i]=='_')) i++
                    add(Token(TokenKind.NAME,input.substring(start,i).lowercase(Locale.ROOT),start))
                }
                c == '(' -> { i++; add(Token(TokenKind.LEFT,"(",start)) }
                c == ')' -> { i++; add(Token(TokenKind.RIGHT,")",start)) }
                c == ',' -> { i++; add(Token(TokenKind.COMMA,",",start)) }
                else -> {
                    val op = listOf(">>>",">>","<<").firstOrNull { input.startsWith(it,i) }
                        ?: when(c) { '×' -> "*"; '÷' -> "/"; '−' -> "-"; else -> c.toString() }
                    if (op !in listOf("+","-","*","/","%","^","!","~","&","|",">>>",">>","<<"))
                        throw CalculationException("Unsupported character at column ${i+1}.")
                    i += if (op.length > 1) op.length else 1
                    add(Token(TokenKind.OP,op,start))
                }
            }
        }
        add(Token(TokenKind.END,"",input.length))
    }
    private var index = 0
    fun peek(): Token = tokens[index]
    fun take(): Token = tokens[index++].also { if (it.kind == TokenKind.END) fail("Expected a value",it) }
    fun expect(kind: TokenKind) {
        if (peek().kind != kind) fail("Expected ${kind.name.lowercase(Locale.ROOT)}",peek())
        if (kind != TokenKind.END) index++
    }
    fun fail(message: String, token: Token = peek()): Nothing = throw CalculationException("$message at column ${token.position+1}.")
}

internal class DecimalParser(input: String) {
    private val t = Tokens(input)
    private val mc get() = Calculator.context
    fun parse(): BigDecimal = expression(0,0).also { t.expect(TokenKind.END) }
    private fun expression(minimum: Int, depth: Int): BigDecimal {
        if (depth >= Calculator.MAX_DEPTH) t.fail("Nesting limit reached")
        val token = t.take()
        var left = when {
            token.kind == TokenKind.NUMBER -> {
                if (!token.text.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))) t.fail("Invalid decimal literal",token)
                val exponent = token.text.substringAfter('e',token.text.substringAfter('E',"0")).toIntOrNull()
                if (exponent == null || exponent !in -1000..1000) t.fail("Exponent limit is ±1000",token)
                Calculator.bounded(BigDecimal(token.text,mc))
            }
            token.kind == TokenKind.LEFT -> expression(0,depth+1).also { t.expect(TokenKind.RIGHT) }
            token.kind == TokenKind.OP && token.text in listOf("+","-") -> expression(25,depth+1).let { if(token.text=="-") it.negate(mc) else it }
            else -> t.fail("Expected a decimal number or parenthesis",token)
        }
        while (true) {
            val op = t.peek().text
            val precedence = when(op) { "+","-" -> 10; "*","/","%" -> 20; "^" -> 30; else -> -1 }
            if (precedence < minimum) return left
            t.take()
            val right = expression(if(op=="^") precedence else precedence+1,depth+1)
            left = Calculator.bounded(when(op) {
                "+" -> left.add(right,mc); "-" -> left.subtract(right,mc); "*" -> left.multiply(right,mc)
                "/" -> { if(right.signum()==0) t.fail("Division by zero"); left.divide(right,mc) }
                "%" -> { if(right.signum()==0) t.fail("Remainder by zero"); left.remainder(right).round(mc) }
                else -> {
                    val power = try { right.intValueExact() } catch (_: ArithmeticException) { t.fail("Decimal powers must be integers") }
                    if (power !in -1000..1000) t.fail("Power limit is ±1000")
                    if (left.signum()==0 && power <= 0) t.fail("Undefined zero power")
                    if (power < 0) BigDecimal.ONE.divide(left.pow(-power,mc),mc) else left.pow(power,mc)
                }
            })
        }
    }
}

internal class ScientificParser(input: String, private val angle: AngleUnit) {
    private val t = Tokens(input)
    fun parse(): Double = expression(0,0).also { t.expect(TokenKind.END) }
    private fun finite(value: Double): Double {
        if (!value.isFinite()) t.fail("Result is outside the finite real-number domain")
        return value
    }
    private fun expression(minimum: Int, depth: Int): Double {
        if (depth >= Calculator.MAX_DEPTH) t.fail("Nesting limit reached")
        val token = t.take()
        var left = when {
            token.kind == TokenKind.NUMBER -> {
                val parsed=token.text.toDoubleOrNull() ?: t.fail("Invalid real number",token)
                if(parsed==0.0 && BigDecimal(token.text).signum()!=0) t.fail("Real literal underflows binary64",token)
                finite(parsed)
            }
            token.kind == TokenKind.LEFT -> expression(0,depth+1).also { t.expect(TokenKind.RIGHT) }
            token.kind == TokenKind.OP && token.text in listOf("+","-") -> expression(25,depth+1).let { if(token.text=="-") -it else it }
            token.kind == TokenKind.NAME -> when(token.text) {
                "pi","π" -> Math.PI
                "e" -> Math.E
                else -> {
                    t.expect(TokenKind.LEFT)
                    val args = mutableListOf(expression(0,depth+1))
                    while(t.peek().kind==TokenKind.COMMA) {
                        t.take(); if(args.size >= 2) t.fail("At most two function arguments")
                        args += expression(0,depth+1)
                    }
                    t.expect(TokenKind.RIGHT)
                    function(token.text,args)
                }
            }
            else -> t.fail("Expected a real number, constant or function",token)
        }
        while(true) {
            if (t.peek().text == "!" && 40 >= minimum) {
                t.take()
                if(t.peek().text=="!") t.fail("Use parentheses for repeated factorial; double factorial is not supported")
                if(left < 0 || left > 170 || left != Math.floor(left)) t.fail("Factorial requires an integer from 0 to 170")
                left = (2..left.toInt()).fold(1.0) { value,n -> value*n }; continue
            }
            val op=t.peek().text
            val precedence=when(op) { "+","-" -> 10; "*","/","%" -> 20; "^" -> 30; else -> -1 }
            if(precedence < minimum) return finite(left)
            t.take(); val right=expression(if(op=="^") precedence else precedence+1,depth+1)
            left=finite(when(op) {
                "+" -> left+right; "-" -> left-right; "*" -> (left*right).also { if(it==0.0 && left!=0.0 && right!=0.0) t.fail("Multiplication underflows binary64") }
                "/" -> { if(right==0.0) t.fail("Division by zero"); (left/right).also { if(it==0.0 && left!=0.0) t.fail("Division underflows binary64") } }
                "%" -> { if(right==0.0) t.fail("Remainder by zero"); left%right }
                else -> { if(left==0.0 && right<=0.0) t.fail("Undefined zero power"); Math.pow(left,right).also { if(it==0.0 && left!=0.0) t.fail("Power underflows binary64") } }
            })
        }
    }
    private fun function(name: String, a: List<Double>): Double {
        val binary = name in listOf("pow","min","max","hypot")
        if(a.size != if(binary) 2 else 1) t.fail("Wrong argument count for $name")
        val x=a[0]
        val radians=if(angle==AngleUnit.DEGREES) Math.toRadians(x) else x
        fun outputAngle(value: Double)=if(angle==AngleUnit.DEGREES) Math.toDegrees(value) else value
        return finite(when(name) {
            "sin" -> Math.sin(radians); "cos" -> Math.cos(radians); "tan" -> {
                if(kotlin.math.abs(Math.cos(radians)) < 1e-15) t.fail("Tangent too close to a singularity")
                Math.tan(radians)
            }
            "asin" -> outputAngle(Math.asin(x)); "acos" -> outputAngle(Math.acos(x)); "atan" -> outputAngle(Math.atan(x))
            "sinh" -> Math.sinh(x); "cosh" -> Math.cosh(x); "tanh" -> Math.tanh(x)
            "sqrt" -> Math.sqrt(x); "cbrt" -> Math.cbrt(x); "ln" -> Math.log(x); "log","log10" -> Math.log10(x)
            "exp" -> Math.exp(x).also { if(it==0.0) t.fail("Exponential underflows binary64") }; "abs" -> kotlin.math.abs(x); "floor" -> Math.floor(x); "ceil" -> Math.ceil(x)
            "round" -> Math.rint(x); "percent" -> x/100.0
            "pow" -> { if(x==0.0 && a[1]<=0.0) t.fail("Undefined zero power"); Math.pow(x,a[1]).also { if(it==0.0 && x!=0.0) t.fail("Power underflows binary64") } }
            "min" -> minOf(x,a[1]); "max" -> maxOf(x,a[1]); "hypot" -> Math.hypot(x,a[1])
            else -> t.fail("Unknown function $name")
        })
    }
}
