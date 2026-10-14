package dev.fusion.documents

import java.io.*
import java.nio.file.Files
import java.security.MessageDigest

/** Production protocol/codec + actual temporary-file I/O. Not a document-provider emulator. */
object CopyChecks {
    private var assertions=0
    private const val EPOCH="12345678-1234-1234-1234-123456789abc"
    private const val TARGET="content://test.documents/document/new-copy"
    private fun verify(ok: Boolean, why: String) { assertions++; check(ok) { why } }
    private fun reject(why: String, block: ()->Unit) {
        var failed=false
        try { block() } catch(_: Exception) { failed=true }
        verify(failed,why)
    }
    private fun original(): EditorSession {
        val doc=TextDocument("doc","draft.txt","content://test.documents/document/source","Frozen 😀\r\n",TextBytes.digest("Original"))
        return EditorSession(listOf(doc),doc.id)
    }
    private fun id(s: EditorSession)=requireNotNull(s.pendingCopy?.requestId)
    private fun roundtrip(s: EditorSession): EditorSession {
        val bytes=ByteArrayOutputStream().also { SessionCodec.write(s,it) }.toByteArray()
        val actual=SessionCodec.read(ByteArrayInputStream(bytes))
        verify(s==actual,"v2 envelope preserves every field exactly before explicit recovery")
        return actual
    }
    private fun reserved(s: EditorSession, target: String=TARGET): EditorSession {
        val request=CopyProtocol.start(s,"doc",EPOCH)
        return CopyProtocol.reserve(CopyProtocol.receive(request,id(request),target),id(request))
    }
    fun run(fixtures: File): Int {
        assertions=0
        val root=Files.createTempDirectory("fusion-copy-contracts-").toFile()
        try {
            val legacyBytes=File(fixtures,"editor-session-v1.bin").readBytes()
            verify(TextBytes.digest(legacyBytes)=="225712d0e470b2ff9706b49fb8c1f6c344608cebd3a39ef983c601f50a6088a0","historical v1 fixture fingerprint")
            val legacy=SessionCodec.read(ByteArrayInputStream(legacyBytes))
            verify(legacy.documents.single().content=="Later edit 😀\r\n" && legacy.pendingCopy?.content=="Frozen BOM \uFEFF漢\r\n","historical production v1 writer migration preserves late edit and frozen payload")
            verify(legacy.pendingCopy?.requestId==null && legacy.pendingCopy?.stage==CopyStage.REVIEW_REQUIRED,"legacy results cannot authorize a write")
            roundtrip(legacy)
            val migrated=CopyProtocol.retry(legacy,null,EPOCH)
            verify(migrated.pendingCopy?.content==legacy.pendingCopy?.content && id(migrated)=="$EPOCH-1","legacy explicit retry creates first correlated identity without replacing payload")

            val longName="a".repeat(511)+"😀tail.txt"
            val label=longName.substring(0,TextSelection(512).inText(longName).anchor)
            verify(label.length==511 && TextBytes.encode(label).size==511,"bounded display label cannot split a surrogate pair and poison a checkpoint")
            val base=original()
            val first=CopyProtocol.start(base,"doc",EPOCH)
            verify(first.pendingCopy?.content==base.documents.single().content && first.copySequence==1L,"snapshot freeze and sequence")
            reject("cannot start a second active payload") { CopyProtocol.start(first,"doc",EPOCH) }
            val edited=first.copy(documents=first.documents.map { it.copy(content="Late edit") })
            val second=CopyProtocol.retry(edited,id(first),EPOCH)
            verify(second.copySequence==2L && second.pendingCopy?.content==first.pendingCopy?.content,"retry rotates identity, not frozen content")
            verify(CopyProtocol.receive(second,id(first),TARGET)===second,"late old selection cannot authorize newer attempt")
            verify(CopyProtocol.receive(second,id(first),null)===second,"late old cancellation cannot cancel newer attempt")
            verify(CopyProtocol.abandon(second,id(first))===second,"stale abandon control cannot abandon newer attempt")
            verify(CopyProtocol.retry(second,id(first),EPOCH)===second,"stale retry control cannot rotate newer attempt")
            val received=CopyProtocol.receive(second,id(second),TARGET)
            verify(received.pendingCopy?.stage==CopyStage.DESTINATION_READY,"result alone is not a write reservation")
            verify(CopyProtocol.receive(received,id(second),"content://test.documents/document/other")===received,"duplicate result cannot retarget destination")
            val writing=CopyProtocol.reserve(received,id(second))
            verify(writing.pendingCopy?.stage==CopyStage.WRITE_RESERVED,"explicit reservation stage")
            verify(CopyProtocol.retry(writing,id(writing),EPOCH)===writing && CopyProtocol.abandon(writing,id(writing))===writing,"in-flight reservation cannot be canceled or retried")
            for(state in listOf(base,first,second,received,writing,CopyProtocol.uncertain(writing,id(writing)))) roundtrip(state)

            // Real file checkpoint/readback at phase boundaries, not a claimed Android crash test.
            val checkpoint=File(root,"session.bin")
            for(state in listOf(received,writing)) {
                FileOutputStream(checkpoint).use { SessionCodec.write(state,it); it.fd.sync() }
                val recovered=CopyProtocol.recover(checkpoint.inputStream().use(SessionCodec::read))
                verify(recovered.pendingCopy?.stage==CopyStage.REVIEW_REQUIRED,"uncertain destination/reservation recovers only to explicit review")
                verify(CopyProtocol.receive(recovered,id(state),TARGET)===recovered,"no automatic replay after uncertain checkpoint")
                verify(CopyProtocol.recover(recovered)==recovered,"recovery is idempotent")
            }
            // Indistinguishable histories: a picker was waiting OR canceled in RAM before the next checkpoint.
            FileOutputStream(checkpoint).use { SessionCodec.write(first,it); it.fd.sync() }
            val abandoned=CopyProtocol.abandon(first,id(first))
            verify(abandoned.pendingCopy==null,"abandon current attempt in memory")
            val recoveredAwaiting=CopyProtocol.recover(checkpoint.inputStream().use(SessionCodec::read))
            verify(CopyProtocol.receive(recoveredAwaiting,id(first),TARGET)===recoveredAwaiting,
                "recovered waiting snapshot must not resurrect an uncheckpointed abandonment")
            // Fresh attacks after recovery repair: replayed cancel and old result after a new epoch/sequence.
            verify(CopyProtocol.receive(recoveredAwaiting,id(first),null)===recoveredAwaiting,"post-recovery old cancel cannot discard frozen payload")
            val fresh=CopyProtocol.retry(recoveredAwaiting,id(first),EPOCH)
            verify(CopyProtocol.receive(fresh,id(first),TARGET)===fresh,"old selected result still refused after explicit recovery retry")
            println("PASS copy identities/recovery: late selection/cancel, retry/abandon races, historical v1 migration, actual phase-file roundtrip and no replay")

            val destination=File(root,"new-copy.txt")
            val source=File(root,"source.txt").apply { writeText("Original on disk") }
            val data=TextBytes.encode(requireNotNull(writing.pendingCopy).content)
            val proof=VerifiedCopy.write(data,output={ FileOutputStream(destination) },reopen={ FileInputStream(destination) },binding=VerifiedCopy.Binding(id(writing),TARGET))
            val success=CopyProtocol.verified(writing,id(writing),proof)
            verify(success.pendingCopy==null && success.copyReceipts.first().outcome==CopyOutcome.VERIFIED,"verified bytes produce a receipt and consume the attempt")
            verify(success.documents.single().content=="Late edit" && success.documents.single().dirty,"late edit survives and stays dirty")
            verify(destination.readBytes().contentEquals(data) && source.readText()=="Original on disk","actual destination bytes and separate original unchanged")
            verify(CopyProtocol.receive(success,id(writing),TARGET)===success && CopyProtocol.verified(success,id(writing),proof)===success,"duplicates after success cannot write or consume another payload")
            roundtrip(success)
            val noLate=reserved(base,"content://test.documents/document/second-copy")
            val otherProof=VerifiedCopy.write(TextBytes.encode("different"),output={ FileOutputStream(File(root,"different.txt")) },reopen={ FileInputStream(File(root,"different.txt")) },binding=VerifiedCopy.Binding(id(noLate),requireNotNull(noLate.pendingCopy?.destinationUri)))
            reject("wrong content proof cannot mark frozen payload saved") { CopyProtocol.verified(noLate,id(noLate),otherProof) }
            // Same bytes are not proof for a different destination/request.
            reject("readback evidence must bind the destination, not just payload bytes") { CopyProtocol.verified(noLate,id(noLate),proof) }

            // New attacks aimed separately at each component of the repaired evidence binding.
            val sameIdOtherDestination=writing.copy(pendingCopy=writing.pendingCopy!!.copy(destinationUri="content://test.documents/document/not-verified"))
            reject("same ID and bytes cannot prove a different destination") { CopyProtocol.verified(sameIdOtherDestination,id(writing),proof) }
            val sameDestinationOtherId=writing.copy(copySequence=3,pendingCopy=writing.pendingCopy!!.copy(requestId="$EPOCH-3"))
            reject("same destination and bytes cannot prove a different attempt") { CopyProtocol.verified(sameDestinationOtherId,id(sameDestinationOtherId),proof) }
            val unbound=VerifiedCopy.write(data,output={ FileOutputStream(File(root,"unbound.txt")) },reopen={ FileInputStream(File(root,"unbound.txt")) })
            reject("unbound legacy helper result cannot mark protocol success") { CopyProtocol.verified(writing,id(writing),unbound) }
            val recoveredLegacy=CopyProtocol.recover(legacy)
            verify(CopyProtocol.receive(recoveredLegacy,id(first),TARGET)===recoveredLegacy,"uncorrelated legacy pending result remains blocked after repair")
            val freshAfterRecovery=CopyProtocol.retry(recoveredAwaiting,id(first),EPOCH)
            verify(id(freshAfterRecovery)!=(id(first)) && CopyProtocol.receive(freshAfterRecovery,id(freshAfterRecovery),TARGET).pendingCopy?.stage==CopyStage.DESTINATION_READY,
                "explicit fresh retry still works after conservative recovery repair")
            val occupied=File(root,"occupied.txt").apply { writeText("Existing data must stay") }
            var outputOpened=false
            reject("nonempty destination refused before output") {
                VerifiedCopy.requireEmpty { occupied.inputStream() }
                outputOpened=true
                occupied.outputStream().use { it.write(data) }
            }
            verify(!outputOpened && occupied.readText()=="Existing data must stay","existing real file retained exactly")
            val empty=File(root,"empty.txt").apply { createNewFile() }
            VerifiedCopy.requireEmpty { empty.inputStream() }
            verify(empty.length()==0L,"actually empty target admitted by observation, not called exclusively new")
            reject("unknown/unreadable emptiness is not zero") { VerifiedCopy.requireEmpty { File(root,"absent.txt").inputStream() } }
            val mutableBytes=TextBytes.encode("safe original")
            val changed=File(root,"caller-mutation.txt")
            VerifiedCopy.write(mutableBytes,output={ mutableBytes.fill('x'.code.toByte()); FileOutputStream(changed) },reopen={ FileInputStream(changed) })
            verify(changed.readText()=="safe original","caller byte-array mutation cannot alter frozen I/O payload")
            val mismatch=File(root,"mismatch.txt")
            reject("real-file mismatched readback rejects completion") {
                VerifiedCopy.write(data,output={ FileOutputStream(mismatch) },reopen={ mismatch.writeText("tampered"); FileInputStream(mismatch) })
            }
            verify(mismatch.exists(),"failed copy never automatically deletes an ambiguous destination")
            reject("real open-output failure") { VerifiedCopy.write(data,output={ FileOutputStream(root) },reopen={ FileInputStream(root) }) }
            println("PASS copy readback: actual files, failure retention, immutable caller bytes and content/destination-bound evidence")

            reject("original URI") { CopyProtocol.receive(first,id(first),base.documents.single().sourceUri) }
            reject("non-content URI") { CopyProtocol.receive(first,id(first),"file:///private/file") }
            val otherSource=first.copy(documents=first.documents+TextDocument("other","other.txt",TARGET,"data",TextBytes.digest("data")))
            reject("other open document URI") { CopyProtocol.receive(otherSource,id(otherSource),TARGET) }
            val repeated=CopyProtocol.retry(CopyProtocol.uncertain(writing,id(writing)),id(writing),EPOCH)
            reject("retry may not target previous uncertain destination") { CopyProtocol.receive(repeated,id(repeated),TARGET) }
            var many=first
            repeat(100) { many=CopyProtocol.retry(many,id(many),EPOCH); roundtrip(many) }
            verify(many.copySequence==101L && many.copyReceipts.size==16,"bounded receipts never reset monotonic identity sequence")
            reject("sequence overflow is fail closed") { CopyProtocol.start(base.copy(copyEpoch=EPOCH,copySequence=Long.MAX_VALUE),"doc",EPOCH) }
            reject("forged phase without destination fails codec") { roundtrip(first.copy(pendingCopy=first.pendingCopy!!.copy(stage=CopyStage.WRITE_RESERVED))) }
            reject("future operation ID does not match sequence") { roundtrip(first.copy(pendingCopy=first.pendingCopy!!.copy(requestId="$EPOCH-2"))) }
            reject("unknown schema with valid checksum") {
                val encoded=ByteArrayOutputStream().also { SessionCodec.write(first,it) }.toByteArray()
                val length=java.nio.ByteBuffer.wrap(encoded).int
                val payload=encoded.copyOfRange(4,4+length)
                java.nio.ByteBuffer.wrap(payload).putInt(4,99)
                val out=ByteArrayOutputStream(); DataOutputStream(out).apply { writeInt(payload.size); write(payload); writeUTF(TextBytes.digest(payload)) }
                SessionCodec.read(ByteArrayInputStream(out.toByteArray()))
            }
            for(uri in listOf("content://", "content:opaque", "content://provider/path#fragment", "content://provider/space here", "file:///a", "content://provider/"+"x".repeat(8192)))
                verify(!CopyProtocol.validDestination(uri),"malformed/oversized destination rejected")
            verify(CopyProtocol.validDestination("content://provider/document/a%20b"),"encoded spaces remain valid")

            // A deterministic event corpus runs actual reducer transitions, not an emulated Android/provider.
            var state=original()
            val random=kotlin.random.Random(590501)
            val visited=mutableSetOf<Pair<Int,CopyStage?>>()
            var filesVerified=0
            repeat(1500) { step ->
                val before=state
                val p=before.pendingCopy
                val requestId=p?.requestId
                val event=random.nextInt(10)
                visited += event to p?.stage
                state=when(event) {
                    0 -> if(p==null) CopyProtocol.start(before,"doc",EPOCH) else before
                    1 -> if(p!=null) CopyProtocol.retry(before,p.requestId,EPOCH) else before
                    2 -> CopyProtocol.receive(before,"$EPOCH-${before.copySequence+100}","content://test.documents/document/stale")
                    3 -> if(requestId!=null) CopyProtocol.receive(before,requestId,"content://test.documents/document/result-${before.copySequence}") else before
                    4 -> if(requestId!=null) CopyProtocol.reserve(before,requestId) else before
                    5 -> if(p?.stage==CopyStage.WRITE_RESERVED) {
                        val target=File(root,"trace-${before.copySequence}.txt")
                        val evidence=VerifiedCopy.write(TextBytes.encode(p.content),output={ FileOutputStream(target) },reopen={ FileInputStream(target) },
                            binding=VerifiedCopy.Binding(requireNotNull(p.requestId),requireNotNull(p.destinationUri)))
                        filesVerified++
                        CopyProtocol.verified(before,requireNotNull(requestId),evidence)
                    } else before
                    6 -> CopyProtocol.abandon(before,p?.requestId)
                    7 -> CopyProtocol.recover(before)
                    8 -> before.copy(documents=before.documents.map {
                        it.copy(content=EditBuffer.create(it.id,it.content).edit("late-$step-😀\r\n").content)
                    })
                    else -> if(requestId!=null) CopyProtocol.receive(before,requestId,null) else before
                }
                val expectedPhase=when(event) {
                    0 -> if(p==null) CopyStage.AWAITING_PICKER else p.stage
                    1 -> if(p==null) null else if(p.stage==CopyStage.WRITE_RESERVED) p.stage else CopyStage.AWAITING_PICKER
                    3 -> if(p?.stage==CopyStage.AWAITING_PICKER) CopyStage.DESTINATION_READY else p?.stage
                    4 -> if(p?.stage==CopyStage.DESTINATION_READY) CopyStage.WRITE_RESERVED else p?.stage
                    5 -> if(p?.stage==CopyStage.WRITE_RESERVED) null else p?.stage
                    6 -> if(p?.stage==CopyStage.WRITE_RESERVED) p.stage else null
                    7 -> if(p!=null) CopyStage.REVIEW_REQUIRED else null
                    9 -> if(p?.stage==CopyStage.AWAITING_PICKER) null else p?.stage
                    else -> p?.stage
                }
                verify(state.pendingCopy?.stage==expectedPhase,"event $event obeys independent phase table")
                verify(state.copySequence>=before.copySequence,"event cannot roll back identity sequence")
                verify(state.copyReceipts.size<=16 && state.copyReceipts.map { it.requestId }.distinct().size==state.copyReceipts.size,"receipt bound and uniqueness under event corpus")
                if(event==2) verify(state===before,"stale event is a complete no-op")
                if(p!=null && state.pendingCopy!=null) verify(state.pendingCopy!!.content==p.content,"active payload stays immutable through late edits/retry/recovery")
                if(step%50==0) roundtrip(state)
            }
            verify(filesVerified>0,"event corpus actually reaches verified real-file effects")
            verify((3 to CopyStage.REVIEW_REQUIRED) in visited && (2 to CopyStage.WRITE_RESERVED) in visited,"attacks reached uncertain/review and in-flight reservation boundaries")
            println("PASS copy event corpus: 1,500 transitions, ${visited.size} event/phase pairs, $filesVerified actual file writes/readbacks")
            println("PASS copy admission/schema: known-source and recent-target refusal, receipt bounds, sequence exhaustion and malformed-state rejection")
            println("PASS: $assertions copy assertions. JVM/file evidence only; no Android picker, SAF provider, device crash or physical durability acceptance.")
            return assertions
        } finally { check(root.deleteRecursively()) }
    }
    @JvmStatic fun main(args: Array<String>) { require(args.size==1); run(File(args[0])) }
}
