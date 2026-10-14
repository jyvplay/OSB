package dev.fusion.documents

/** PR-01 / FF-15 / FM-18: protocol state is not evidence that a provider performed an effect. */
enum class CopyStage { AWAITING_PICKER, DESTINATION_READY, WRITE_RESERVED, REVIEW_REQUIRED }
enum class CopyOutcome { VERIFIED, CANCELED, ABANDONED, UNVERIFIED }
data class CopyReceipt(val requestId: String, val documentId: String, val destinationUri: String?,
                       val outcome: CopyOutcome, val digest: String? = null, val bytes: Int? = null)

/**
 * One active frozen payload. IDs are an installation/session epoch + persisted monotonic sequence.
 * The Android host MUST checkpoint WRITE_RESERVED before opening output, and MUST NOT replay it
 * after recovery. This is not exactly-once provider I/O or a physical power-loss durability proof.
 */
object CopyProtocol {
    const val MAX_RECEIPTS = 16
    private val epochPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun validEpoch(epoch: String): Boolean = epochPattern.matches(epoch)
    fun validRequestId(id: String): Boolean {
        if(id.length !in 38..56 || id[36]!='-') return false
        val sequence=id.substring(37).toLongOrNull() ?: return false
        return validEpoch(id.substring(0,36)) && sequence>0 && sequence.toString()==id.substring(37)
    }
    fun validDestination(raw: String): Boolean {
        if(raw.length>8192) return false
        return try {
            val uri=java.net.URI(raw)
            TextBytes.encodedSize(raw)<=8192 && uri.scheme.equals("content",ignoreCase=true) &&
                !uri.isOpaque && !uri.rawAuthority.isNullOrBlank() && uri.rawFragment==null
        } catch(_: IllegalArgumentException) { false } catch(_: java.net.URISyntaxException) { false }
    }
    private fun next(s: EditorSession, epochIfNew: String): Pair<String,Long> {
        val epoch=s.copyEpoch ?: epochIfNew
        require(validEpoch(epoch)) { "Invalid copy epoch" }
        require(s.copySequence in 0 until Long.MAX_VALUE) { "Copy sequence exhausted; no request identity may be reused" }
        return epoch to s.copySequence+1
    }
    fun start(s: EditorSession, documentId: String, epochIfNew: String): EditorSession {
        require(s.pendingCopy==null) { "A frozen copy is already pending" }
        val doc=s.documents.singleOrNull { it.id==documentId } ?: error("Document is no longer open")
        val (epoch,sequence)=next(s,epochIfNew)
        val pending=PendingCopy(doc.id,doc.name,doc.sourceUri,doc.content,"$epoch-$sequence",CopyStage.AWAITING_PICKER)
        return s.copy(pendingCopy=pending,copyEpoch=epoch,copySequence=sequence)
    }
    fun retry(s: EditorSession, expectedId: String?, epochIfNew: String): EditorSession {
        val old=s.pendingCopy ?: return s
        if(old.requestId!=expectedId || old.stage==CopyStage.WRITE_RESERVED) return s
        val (epoch,sequence)=next(s,epochIfNew)
        val retired=archive(s,old,if(old.destinationUri==null) CopyOutcome.ABANDONED else CopyOutcome.UNVERIFIED)
        return retired.copy(pendingCopy=old.copy(requestId="$epoch-$sequence",stage=CopyStage.AWAITING_PICKER,destinationUri=null),
            copyEpoch=epoch,copySequence=sequence)
    }
    /** A user abandonment is different from an attributed OS picker cancellation. */
    fun abandon(s: EditorSession, expectedId: String?): EditorSession {
        val p=s.pendingCopy ?: return s
        if(p.requestId!=expectedId || p.stage==CopyStage.WRITE_RESERVED) return s
        return archive(s,p,if(p.destinationUri==null) CopyOutcome.ABANDONED else CopyOutcome.UNVERIFIED).copy(pendingCopy=null)
    }
    fun receive(s: EditorSession, requestId: String, destination: String?): EditorSession {
        val p=s.pendingCopy ?: return s
        if(p.requestId!=requestId || p.stage!=CopyStage.AWAITING_PICKER) return s
        if(destination==null) return archive(s,p,CopyOutcome.CANCELED).copy(pendingCopy=null)
        require(validDestination(destination)) {
            "Destination must be a bounded content-provider URI"
        }
        require(destination!=p.sourceUri) { "Refusing the original source URI" }
        require(s.documents.none { it.sourceUri==destination }) { "Destination is another open document's source" }
        require(s.copyReceipts.none { it.destinationUri==destination }) { "Choose a new destination, not a recent copy or partial destination" }
        return s.copy(pendingCopy=p.copy(stage=CopyStage.DESTINATION_READY,destinationUri=destination))
    }
    fun reserve(s: EditorSession, requestId: String): EditorSession {
        val p=s.pendingCopy ?: return s
        if(p.requestId!=requestId || p.stage!=CopyStage.DESTINATION_READY) return s
        return s.copy(pendingCopy=p.copy(stage=CopyStage.WRITE_RESERVED))
    }
    fun uncertain(s: EditorSession, requestId: String): EditorSession {
        val p=s.pendingCopy ?: return s
        if(p.requestId!=requestId) return s
        return archive(s,p,CopyOutcome.UNVERIFIED).copy(pendingCopy=p.copy(stage=CopyStage.REVIEW_REQUIRED))
    }
    /** Called explicitly by recovery owner, not by the codec's readback comparison. Never writes a URI. */
    fun recover(s: EditorSession): EditorSession {
        val p=s.pendingCopy ?: return s
        return if(p.requestId!=null && p.stage in setOf(CopyStage.AWAITING_PICKER,CopyStage.DESTINATION_READY,CopyStage.WRITE_RESERVED)) uncertain(s,p.requestId) else s
    }
    fun verified(s: EditorSession, requestId: String, evidence: VerifiedCopy.Readback): EditorSession {
        val p=s.pendingCopy ?: return s
        if(p.requestId!=requestId || p.stage!=CopyStage.WRITE_RESERVED) return s
        require(evidence.binding==VerifiedCopy.Binding(requestId,requireNotNull(p.destinationUri))) {
            "Readback evidence belongs to a different request or destination"
        }
        require(evidence.digest==TextBytes.digest(p.content) && evidence.bytes==TextBytes.encodedSize(p.content)) {
            "Readback evidence does not describe the frozen payload"
        }
        val receipt=CopyReceipt(requestId,p.documentId,p.destinationUri,CopyOutcome.VERIFIED,evidence.digest,evidence.bytes)
        return addReceipt(s,receipt).copy(documents=s.documents.map { if(it.id==p.documentId) it.saved(p.content) else it },pendingCopy=null)
    }
    private fun archive(s: EditorSession, p: PendingCopy, outcome: CopyOutcome): EditorSession =
        p.requestId?.let { addReceipt(s,CopyReceipt(it,p.documentId,p.destinationUri,outcome)) } ?: s
    private fun addReceipt(s: EditorSession, receipt: CopyReceipt): EditorSession =
        s.copy(copyReceipts=(listOf(receipt)+s.copyReceipts.filterNot { it.requestId==receipt.requestId }).take(MAX_RECEIPTS))
}
