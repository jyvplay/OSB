package dev.fusion.calc

import java.io.*
import java.security.MessageDigest

data class CalculationRecord(val id: String, val atMillis: Long, val calculation: Calculation)
data class CalculatorSession(val request: CalculationRequest = CalculationRequest(),
                             val history: List<CalculationRecord> = emptyList())

/** PR-09: bounded local session/history; read never re-executes stored expressions. Checksum is not encryption/authentication. */
object CalculatorHistory {
    const val MAX_ENTRIES=100
    const val MAX_BYTES=256*1024
    fun append(session: CalculatorSession, record: CalculationRecord): CalculatorSession {
        validateRecord(record)
        require(session.history.none { it.id==record.id }) { "Duplicate calculation identity" }
        return session.copy(history=(listOf(record)+session.history).take(MAX_ENTRIES))
    }
    fun write(session: CalculatorSession, output: OutputStream) {
        validate(session)
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeInt(0x46534341); data.writeInt(1)
            data.request(session.request); data.writeInt(session.history.size)
            session.history.forEach {
                data.writeUTF(it.id); data.writeLong(it.atMillis); data.request(it.calculation.request)
                data.writeUTF(it.calculation.value); data.writeUTF(it.calculation.explanation)
            }
        }
        val bytes=buffer.toByteArray(); require(bytes.size<=MAX_BYTES)
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); write(hash(bytes)); flush() }
    }
    fun read(input: InputStream): CalculatorSession {
        val data=DataInputStream(input)
        val size=data.readInt(); require(size in 1..MAX_BYTES) { "Invalid calculator snapshot length" }
        val bytes=ByteArray(size).also(data::readFully)
        val expected=ByteArray(32).also(data::readFully)
        require(MessageDigest.isEqual(expected,hash(bytes))) { "Calculator recovery checksum mismatch" }
        require(data.read()==-1) { "Trailing calculator recovery bytes" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { payload ->
            require(payload.readInt()==0x46534341 && payload.readInt()==1) { "Unsupported calculator recovery version" }
            val request=payload.request(); val count=payload.readInt(); require(count in 0..MAX_ENTRIES)
            val history=List(count) { CalculationRecord(payload.readUTF(),payload.readLong(),
                Calculation(payload.request(),payload.readUTF(),payload.readUTF())) }
            require(payload.read()==-1)
            CalculatorSession(request,history).also(::validate)
        }
    }
    private fun validateRequest(request: CalculationRequest) {
        require(request.expression.length<=Calculator.MAX_INPUT)
        Units.definition(request.fromUnit); Units.definition(request.toUnit)
        // Incomplete input is a recoverable draft, not a calculation to execute or discard.
    }
    private fun validateRecord(record: CalculationRecord) {
        require(record.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && record.atMillis>=0)
        validateRequest(record.calculation.request)
        require(record.calculation.value.length in 1..256 && record.calculation.explanation.length in 1..1024)
    }
    private fun validate(session: CalculatorSession) {
        validateRequest(session.request)
        require(session.history.size<=MAX_ENTRIES)
        require(session.history.map { it.id }.distinct().size==session.history.size)
        session.history.forEach(::validateRecord)
    }
    private fun DataOutputStream.request(r: CalculationRequest) {
        writeUTF(r.mode.name); writeUTF(r.expression); writeUTF(r.angle.name); writeUTF(r.radix.name); writeUTF(r.fromUnit); writeUTF(r.toUnit)
    }
    private fun DataInputStream.request()=CalculationRequest(CalculatorMode.valueOf(readUTF()),readUTF(),
        AngleUnit.valueOf(readUTF()),OutputRadix.valueOf(readUTF()),readUTF(),readUTF())
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
}
