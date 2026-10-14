package dev.fusion.calc

import java.io.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Locale
import kotlin.random.Random

/** Independent held-out expectations and mathematical identities; executes the production kernels. */
object CalculatorChecks {
    private var assertions=0
    private fun checkThat(ok: Boolean, description: String) { assertions++; check(ok) { description } }
    private fun result(text: String, mode: CalculatorMode=CalculatorMode.DECIMAL, angle: AngleUnit=AngleUnit.RADIANS): Calculation =
        Calculator.calculate(CalculationRequest(mode,text,angle)).getOrElse { error("$mode [$text] unexpectedly failed: ${it.message}") }
    private fun decimal(text: String, expected: String) = checkThat(BigDecimal(result(text).value).compareTo(BigDecimal(expected))==0,"Decimal [$text] = $expected")
    private fun approximate(text: String, expected: Double, angle: AngleUnit=AngleUnit.RADIANS) {
        val actual=result(text,CalculatorMode.SCIENTIFIC,angle).value.toDouble()
        checkThat(kotlin.math.abs(actual-expected) <= 1e-12*maxOf(1.0,kotlin.math.abs(expected)),"Scientific [$text] expected $expected, got $actual")
    }
    private fun reject(text: String, mode: CalculatorMode=CalculatorMode.DECIMAL) {
        checkThat(Calculator.calculate(CalculationRequest(mode,text)).isFailure,"Must reject $mode [$text]")
    }
    private fun fails(description: String, block: ()->Unit) {
        var failed=false
        try { block() } catch(_: Exception) { failed=true }
        checkThat(failed,description)
    }
    fun run(): Int {
        assertions=0
        decimal("0.1 + 0.2","0.3"); decimal("2+3*4","14"); decimal("(2+3)*4","20")
        decimal("2^3^2","512"); decimal("-2^2","-4"); decimal("(-2)^2","4"); decimal("2^-3","0.125")
        decimal("1e3 + 1E-2","1000.01"); decimal(".5 × 8 ÷ 2","2"); decimal("-5%2","-1")
        decimal("1e1000%3","1"); decimal("1/3","0.3333333333333333333333333333333333")
        for(text in listOf("", "1/0", "0^0", "0^-1", "9^0.5", "2(3)", "NaN", "Infinity", "1e1001", "1e-1001", "1e", "0xFF", "2++", "1;2", "1e1000*10")) reject(text)
        reject("(".repeat(33)+"1"+")".repeat(33)); reject("1".repeat(513)); reject("1+".repeat(128)+"1")
        decimal("(".repeat(31)+"1"+")".repeat(31),"1")
        reject("(".repeat(32)+"1"+")".repeat(32))
        decimal("1"+" ".repeat(511),"1")
        decimal("+"+"1+".repeat(127)+"1","128") // exactly 256 tokens
        val random=Random(390901)
        // Independent exact-integer oracle avoids copying the evaluator's parsing algorithm.
        repeat(400) {
            val a=random.nextInt(-10000,10001); val b=random.nextInt(-10000,10001); val c=random.nextInt(-10000,10001)
            val expected=BigInteger.valueOf(a.toLong()).add(BigInteger.valueOf(b.toLong()).multiply(BigInteger.valueOf(c.toLong())))
            decimal("($a)+($b)*($c)",expected.toString())
        }
        println("PASS calculator decimal: precedence, right powers, finite bounds, exact short decimals, 400 independent integer-oracle cases")

        approximate("sin(pi/2)",1.0); approximate("cos(180)",-1.0,AngleUnit.DEGREES)
        approximate("asin(1)",90.0,AngleUnit.DEGREES); approximate("ln(e)",1.0)
        approximate("log10(1000)",3.0); approximate("sqrt(81)+cbrt(-8)",7.0)
        approximate("hypot(3,4)",5.0); approximate("pow(2,3)+max(4,5)",13.0)
        approximate("5!+percent(50)",120.5); approximate("0!",1.0); approximate("round(2.5)+round(3.5)",6.0)
        approximate("-2^2",-4.0); approximate("2^-2",0.25); approximate("2^3^2",512.0)
        for(text in listOf("sqrt(-1)","ln(0)","asin(2)","171!","(-1)!","1.5!","5!!","pow(0,0)","pow(2)","sin(1,2)","hypot(1,2,3)","eval(1)","1e309","1e-400","exp(-10000)","1e-200*1e-200","1e-200/1e200","0/0")) reject(text,CalculatorMode.SCIENTIFIC)
        checkThat(Calculator.calculate(CalculationRequest(CalculatorMode.SCIENTIFIC,"tan(90)",AngleUnit.DEGREES)).isFailure,"guard tangent singularity")
        for(degrees in -170..170 step 10) {
            val x=Math.toRadians(degrees.toDouble())
            approximate("sin($x)^2+cos($x)^2",1.0)
        }
        println("PASS calculator scientific: independent constants/identities, declared tolerances, domains, underflow and patched repeated-factorial attacks")

        fun word(text: String)=result(text,CalculatorMode.PROGRAMMER).value.toLong()
        checkThat(word("0xffffffffffffffff")==-1L,"unsigned literal interpreted as signed bit pattern")
        checkThat(word("0x7fffffffffffffff+1")==Long.MIN_VALUE,"wrap at sign bit")
        checkThat(word("-1 >>> 1")==Long.MAX_VALUE,"logical shift")
        checkThat(word("-1 >> 1")==-1L,"arithmetic shift")
        checkThat(word("~0 & 0xff")==255L,"NOT/AND")
        checkThat(word("1 << 2+1")==8L,"shift precedence")
        checkThat(word("5^3")==6L,"XOR not power")
        checkThat(word("0b1010 | 0o7")==15L,"prefix bases")
        for(text in listOf("1<<64","1>>-1","0x10000000000000000","18446744073709551616","1.5","1e2","0b102","0x","1/0")) reject(text,CalculatorMode.PROGRAMMER)
        val mask=BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        repeat(400) {
            val a=random.nextLong(); val b=random.nextLong()
            val exact=BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).and(mask).toLong()
            checkThat(word("($a)*($b)")==exact,"64-bit multiplication against independent arbitrary-width oracle")
        }
        checkThat(Calculator.calculate(CalculationRequest(CalculatorMode.PROGRAMMER,"-1",radix=OutputRadix.HEX)).getOrThrow().value=="0xFFFFFFFFFFFFFFFF","hex output preserves 64 bits")
        println("PASS calculator programmer: signed/logical shifts, literal bounds, wrap semantics, 400 arbitrary-width oracle cases")

        fun convert(amount: String,from: String,to: String,expected: String)=checkThat(Units.convert(BigDecimal(amount),from,to).compareTo(BigDecimal(expected))==0,"$amount $from -> $expected $to")
        convert("1","in","cm","2.54"); convert("1","lb","kg","0.45359237"); convert("1","KiB","B","1024")
        convert("1","kB","B","1000"); convert("1","B","bit","8"); convert("0","C","F","32")
        convert("0","F","C","-17."+"7".repeat(31)+"8");
        convert("-40","C","F","-40"); convert("32","F","K","273.15"); convert("-273.15","C","K","0")
        convert("36","km/h","m/s","10"); convert("1","ft2","m2","0.09290304"); convert("1","L","mL","1000")
        fails("dimension mismatch") { Units.convert(BigDecimal.ONE,"m","kg") }
        fails("unknown unit") { Units.convert(BigDecimal.ONE,"month","s") }
        fails("below absolute zero") { Units.convert(BigDecimal("-273.15001"),"C","K") }
        for(a in Units.definitions) for(b in Units.definitions.filter { it.dimension==a.dimension }) {
            val amount=BigDecimal("300.125")
            val back=Units.convert(Units.convert(amount,a.id,b.id),b.id,a.id)
            checkThat(back.subtract(amount).abs()<=BigDecimal("1e-25"),"affine/rational roundtrip ${a.id}/${b.id}")
        }
        // New attacks after the 0 F -> C early-rounding repair; no inherited pass.
        for(f in -450..250 step 7) {
            val independent=BigDecimal(f-32).multiply(BigDecimal(5)).divide(BigDecimal(9),MathContext(100)).round(Calculator.context)
            checkThat(Units.convert(BigDecimal(f),"F","C").compareTo(independent)==0,"single-round affine result $f F")
        }
        fails("oversized unit amount") { Units.convert(BigDecimal("1."+"1".repeat(100)),"m","cm") }
        fails("oversized unit id") { Units.definition("x".repeat(513)) }
        println("PASS calculator units: exact standard factors, decimal/binary data distinction, temperature offset/domain and all same-dimension roundtrips")

        var session=CalculatorSession(CalculationRequest(expression="unfinished ("))
        repeat(105) { i -> session=CalculatorHistory.append(session,CalculationRecord("record-$i",i.toLong(),result("$i+1"))) }
        checkThat(session.history.size==100 && session.history.last().id=="record-5","history bounded and oldest dropped")
        val bytes=ByteArrayOutputStream().also { CalculatorHistory.write(session,it) }.toByteArray()
        checkThat(CalculatorHistory.read(ByteArrayInputStream(bytes))==session,"history context + incomplete draft roundtrip, no execution")
        fails("truncated history") { CalculatorHistory.read(ByteArrayInputStream(bytes.copyOf(bytes.size-1))) }
        val corrupt=bytes.copyOf().also { it[20]=(it[20].toInt() xor 1).toByte() }
        fails("corrupt history") { CalculatorHistory.read(ByteArrayInputStream(corrupt)) }
        fails("oversized snapshot") { CalculatorHistory.read(ByteArrayInputStream(byteArrayOf(127,127,127,127))) }
        fails("trailing data") { CalculatorHistory.read(ByteArrayInputStream(bytes+byteArrayOf(1))) }
        fails("duplicate identities") { CalculatorHistory.write(session.copy(history=listOf(session.history.first(),session.history.first())),ByteArrayOutputStream()) }
        val temporary=File.createTempFile("fusion-calculator-", ".bin")
        try {
            temporary.outputStream().use { CalculatorHistory.write(session,it) }
            checkThat(temporary.inputStream().use(CalculatorHistory::read)==session,"actual filesystem history readback")
        } finally { check(temporary.delete()) }
        val contexts=CalculatorMode.entries.flatMap { mode -> AngleUnit.entries.flatMap { angle -> OutputRadix.entries.map { radix ->
            CalculationRequest(mode,"1+1",angle,radix,"C","F")
        } } }
        val mixed=CalculatorSession(history=contexts.mapIndexed { i,request ->
            CalculationRecord("context-$i",i.toLong(),Calculator.calculate(request).getOrThrow())
        })
        val mixedBytes=ByteArrayOutputStream().also { CalculatorHistory.write(mixed,it) }.toByteArray()
        checkThat(CalculatorHistory.read(ByteArrayInputStream(mixedBytes))==mixed,"all mode/angle/radix and non-default units survive recall codec")
        // A checksum is not authentication: re-checksum a future version to test the schema gate itself.
        val payload=bytes.copyOfRange(4,bytes.size-32)
        java.nio.ByteBuffer.wrap(payload).putInt(4,99)
        val future=ByteArrayOutputStream().also { stream -> DataOutputStream(stream).apply {
            writeInt(payload.size); write(payload); write(java.security.MessageDigest.getInstance("SHA-256").digest(payload))
        } }.toByteArray()
        fails("unsupported history version, even with valid checksum") { CalculatorHistory.read(ByteArrayInputStream(future)) }
        val alphabet="0123456789.e+-*/%^!~&|<>() ,sinx"
        repeat(200) {
            val input=buildString { repeat(random.nextInt(1,35)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            for(mode in CalculatorMode.entries) {
                val request=CalculationRequest(mode,input)
                val first=Calculator.calculate(request); val second=Calculator.calculate(request)
                checkThat(first.getOrNull()==second.getOrNull() && first.exceptionOrNull()?.message==second.exceptionOrNull()?.message,
                    "bounded malformed-input corpus is deterministic and does not escape the Result boundary")
            }
        }
        val oldLocale=Locale.getDefault()
        try { Locale.setDefault(Locale.GERMANY); decimal("0.1+0.2","0.3"); reject("1,5+2") } finally { Locale.setDefault(oldLocale) }
        println("PASS calculator history: bound/context integrity, corruption/truncation/size rejection, actual filesystem roundtrip, locale attack")
        println("PASS: $assertions calculator assertions. No Android UI, AtomicFile, accessibility or physical-device acceptance inferred.")
        return assertions
    }
    @JvmStatic fun main(args: Array<String>) { run() }
}
