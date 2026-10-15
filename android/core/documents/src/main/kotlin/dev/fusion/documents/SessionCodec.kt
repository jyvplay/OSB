package dev.fusion.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** FF-15: versioned, bounded recovery snapshot, including the immutable picker payload. */
data class PendingCopy(val documentId: String, val name: String, val sourceUri: String?, val content: String,
                       val requestId: String? = null, val stage: CopyStage = CopyStage.REVIEW_REQUIRED,
                       val destinationUri: String? = null, val encoding: TextEncoding = TextEncoding.UTF8)
data class EditorSession(
    val documents: List<TextDocument> = emptyList(),
    val selectedId: String? = null,
    val pendingCopy: PendingCopy? = null,
    val copyEpoch: String? = null,
    val copySequence: Long = 0,
    val copyReceipts: List<CopyReceipt> = emptyList(),
)

object SessionCodec {
    const val MAX_DOCUMENTS = 8
    const val MAX_SNAPSHOT_BYTES = (MAX_DOCUMENTS + 1) * TextBytes.MAX_BYTES + 512 * 1024
    private const val MAGIC = 0x46534544 // FSED
    private const val VERSION = 3

    fun write(session: EditorSession, destination: OutputStream) {
        validate(session)
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeInt(session.documents.size)
            session.documents.forEach { doc ->
                out.field(doc.id); out.field(doc.name); out.optional(doc.sourceUri)
                out.field(doc.content); out.field(doc.savedDigest); out.field(doc.encoding.name)
            }
            out.optional(session.selectedId)
            out.writeBoolean(session.pendingCopy != null)
            session.pendingCopy?.let {
                out.field(it.documentId); out.field(it.name); out.optional(it.sourceUri); out.field(it.content)
                out.optional(it.requestId); out.field(it.stage.name); out.optional(it.destinationUri); out.field(it.encoding.name)
            }
            out.optional(session.copyEpoch); out.writeLong(session.copySequence)
            out.writeInt(session.copyReceipts.size)
            session.copyReceipts.forEach {
                out.field(it.requestId); out.field(it.documentId); out.optional(it.destinationUri); out.field(it.outcome.name)
                out.optional(it.digest); out.writeBoolean(it.bytes!=null); it.bytes?.let(out::writeInt)
            }
        }
        val bytes = payload.toByteArray()
        require(bytes.size <= MAX_SNAPSHOT_BYTES)
        val out = DataOutputStream(destination)
        out.writeInt(bytes.size); out.write(bytes); out.writeUTF(TextBytes.digest(bytes)); out.flush()
    }

    fun read(source: InputStream): EditorSession {
        val input = DataInputStream(source)
        val length = input.readInt()
        require(length in 1..MAX_SNAPSHOT_BYTES) { "Invalid recovery length" }
        val bytes = ByteArray(length).also(input::readFully)
        require(input.readUTF() == TextBytes.digest(bytes)) { "Recovery checksum mismatch" }
        require(input.read() == -1) { "Trailing recovery data" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "Not a Fusion editor snapshot" }
            val version=data.readInt()
            require(version in 1..VERSION) { "Unsupported recovery version; snapshot retained" }
            val count = data.readInt()
            require(count in 0..MAX_DOCUMENTS)
            val docs = List(count) {
                TextDocument(data.field(80), data.field(4096), data.optional(8192),
                    data.field(TextBytes.MAX_BYTES), data.field(64), if(version>=3) TextEncoding.valueOf(data.field(32)) else TextEncoding.UTF8)
            }
            val selected = data.optional(80)
            val pending = if (data.readBoolean()) {
                val old=PendingCopy(data.field(80), data.field(4096), data.optional(8192), data.field(TextBytes.MAX_BYTES))
                if(version==1) old // Legacy payload has no correlation identity: explicit review/retry only.
                else old.copy(requestId=data.optional(80),stage=CopyStage.valueOf(data.field(32)),destinationUri=data.optional(8192),
                    encoding=if(version>=3) TextEncoding.valueOf(data.field(32)) else TextEncoding.UTF8)
            } else null
            val epoch=if(version>=2) data.optional(36) else null
            val sequence=if(version>=2) data.readLong() else 0L
            val receiptCount=if(version>=2) data.readInt() else 0
            require(receiptCount in 0..CopyProtocol.MAX_RECEIPTS)
            val receipts=List(receiptCount) { CopyReceipt(data.field(80),data.field(80),data.optional(8192),
                CopyOutcome.valueOf(data.field(32)),data.optional(64),if(data.readBoolean()) data.readInt() else null) }
            require(data.read() == -1)
            EditorSession(docs, selected, pending,epoch,sequence,receipts).also(::validate)
        }
    }

    private fun validate(s: EditorSession) {
        require(s.documents.size <= MAX_DOCUMENTS)
        require(s.copySequence>=0)
        require(if(s.copyEpoch==null) s.copySequence==0L else CopyProtocol.validEpoch(s.copyEpoch) && s.copySequence>0)
        require(s.copyReceipts.size<=CopyProtocol.MAX_RECEIPTS)
        require(s.copyReceipts.map { it.requestId }.distinct().size==s.copyReceipts.size)
        s.copyReceipts.forEach { r ->
            require(CopyProtocol.validRequestId(r.requestId) && r.documentId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(s.copyEpoch!=null && r.requestId.startsWith(s.copyEpoch+"-") && r.requestId.substring(37).toLong()<=s.copySequence)
            require(r.destinationUri==null || CopyProtocol.validDestination(r.destinationUri))
            if(r.outcome==CopyOutcome.VERIFIED) {
                require(r.destinationUri!=null && r.digest?.matches(Regex("[0-9a-f]{64}"))==true && r.bytes!=null && r.bytes in 0..TextBytes.MAX_BYTES)
            } else require(r.digest==null && r.bytes==null)
        }
        val ids = s.documents.map { it.id }
        require(ids.distinct().size == ids.size)
        require(s.selectedId == null || s.selectedId in ids)
        s.documents.forEach {
            require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(TextBytes.encode(it.name).size <= 4096)
            require(it.sourceUri == null || TextBytes.encode(it.sourceUri).size <= 8192)
            require(it.savedDigest.matches(Regex("[0-9a-f]{64}")))
            TextBytes.encode(it.content)
        }
        s.pendingCopy?.let { p ->
            require(p.documentId in ids)
            require(TextBytes.encode(p.name).size <= 4096)
            require(p.sourceUri == null || TextBytes.encode(p.sourceUri).size <= 8192)
            TextEncodingCodec.encode(p.content,p.encoding)
            if(p.requestId==null) require(p.stage==CopyStage.REVIEW_REQUIRED && p.destinationUri==null)
            else require(s.copyEpoch!=null && p.requestId=="${s.copyEpoch}-${s.copySequence}" && CopyProtocol.validRequestId(p.requestId))
            require(p.destinationUri==null || CopyProtocol.validDestination(p.destinationUri))
            if(p.stage in setOf(CopyStage.DESTINATION_READY,CopyStage.WRITE_RESERVED)) require(p.destinationUri!=null)
            if(p.stage==CopyStage.AWAITING_PICKER) require(p.destinationUri==null)
            require(s.copyReceipts.none { it.requestId==p.requestId && it.outcome==CopyOutcome.VERIFIED })
        }
    }

    private fun DataOutputStream.field(s: String) {
        val bytes = TextBytes.encode(s); writeInt(bytes.size); write(bytes)
    }
    private fun DataOutputStream.optional(s: String?) { writeBoolean(s != null); if (s != null) field(s) }
    private fun DataInputStream.field(max: Int): String {
        val size = readInt(); require(size in 0..max) { "Invalid recovery field length" }
        return TextBytes.decode(ByteArray(size).also(::readFully))
    }
    private fun DataInputStream.optional(max: Int): String? = if (readBoolean()) field(max) else null
}
