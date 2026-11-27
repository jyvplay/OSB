// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.alerts.InexactAlarmPlanner
import dev.fusion.automation.MacroExchangeCodec
import dev.fusion.a11y.KeyToTouchMapper
import dev.fusion.capture.DuplicateFrameDetector
import dev.fusion.find.QuerySynonymRingExpander
import dev.fusion.archive.CpioNewcParser
import dev.fusion.lok.OdfMetadataScrubber
import dev.fusion.format.pdf.PdfAnnotationPlan
import dev.fusion.psd.PsdThumbnailInspector
import dev.fusion.hw.OemThrottleDetector
import dev.fusion.maps.GeohashCodec
import dev.fusion.media.QueueShufflePlanner
import dev.fusion.messages.MessageThread
import dev.fusion.model.DurationPhraseParser
import dev.fusion.notes.NoteTagHierarchy
import dev.fusion.privacy.DiagnosticExportAllowlist
import dev.fusion.terminal.TerminalUrlDetector
import dev.fusion.together.EditLogCoalescer
import dev.fusion.vault.PasswordHistoryHashChain
import dev.fusion.browser.model.SubresourceIntegrityParser
import dev.fusion.airplay.NtpTimestampCodec
import java.io.File

/**
 * R146 host probe: executes the twenty-one R146 production lanes on a real
 * JVM and writes transport-encoded verdicts for the independent Python
 * oracle to diff.
 *
 * Case file line: `caseId \t feature \t payload`.
 * Result line:    `caseId \t feature \t enc(result)`.
 * `result` is either `!REF` (the production code refused the input — any
 * IllegalArgumentException / IndexOutOfBounds / NumberFormat thrown inside a
 * lane) or the lane's canonical output string.
 *
 * Transport encoding `enc` percent-encodes the bytes of
 * `% , | ~ ; # : = @ <TAB> <LF> <CR>` as `%XX` (uppercase hex); decoding is
 * the inverse. Corpus generation splits structure before decoding, so raw
 * structure characters inside a payload are owned by the lane grammar.
 */
object R146Checks {
    const val VERSION = "R146"
    private val SPECIALS = charArrayOf('%', ',', '|', '~', ';', '#', ':', '=', '@', '\t', '\n', '\r')
    private const val HEX_DIGITS = "0123456789ABCDEF"

    fun enc(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) {
            if (c in SPECIALS || c.code < 0x20) {
                out.append('%')
                out.append(HEX_DIGITS[(c.code shr 4) and 0xf])
                out.append(HEX_DIGITS[c.code and 0xf])
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    fun dec(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                require(i + 2 < s.length) { "truncated escape" }
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                require(hi >= 0 && lo >= 0) { "bad escape" }
                out.append(((hi shl 4) or lo).toChar())
                i += 3
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd hex length" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "bad hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun segs(payload: String): List<String> = payload.split('~').map { dec(it) }

    fun dispatch(feature: String, payload: String): String = when (feature) {
        "inexact" -> {
            val p = segs(payload)
            require(p.size == 3) { "inexact needs 3 segments" }
            val now = p[0].toLongOrNull() ?: throw IllegalArgumentException("now not an int")
            val grid = p[1].toLongOrNull() ?: throw IllegalArgumentException("grid not an int")
            val specs = p[2].split(',')
            val alarms = specs.map { spec ->
                val f = spec.split(':')
                require(f.size == 5) { "alarm spec needs 5 fields" }
                require(f[4] == "0" || f[4] == "1") { "exact flag must be 0 or 1" }
                InexactAlarmPlanner.Alarm(
                    f[0],
                    f[1].toLongOrNull() ?: throw IllegalArgumentException("interval not an int"),
                    f[2].toLongOrNull() ?: throw IllegalArgumentException("due not an int"),
                    f[3].toLongOrNull() ?: throw IllegalArgumentException("flex not an int"),
                    f[4] == "1",
                )
            }
            val plan = InexactAlarmPlanner.plan(now, grid, alarms)
            val perAlarm = plan.alarms.joinToString(",") { a ->
                val kind = if (a.exact) "EXACT" else "INEXACT"
                "${a.id}:$kind:${a.dueEpochMs}:${a.windowStartEpochMs}:${a.windowEndEpochMs}"
            }
            val batched = plan.batches.joinToString(",") { b ->
                "${b.keyEpochMs}:" + b.ids.joinToString("+")
            }
            "$perAlarm#$batched"
        }
        "keyres" -> {
            val p = segs(payload)
            require(p.size == 2) { "keyres needs 2 segments" }
            KeyToTouchMapper.resolve(p[0], p[1])
        }
        "keyseq" -> {
            val p = segs(payload)
            require(p.size == 3) { "keyseq needs 3 segments" }
            val cooldown = p[2].toIntOrNull() ?: throw IllegalArgumentException("cooldown not an int")
            KeyToTouchMapper.sequence(p[0], p[1], cooldown)
        }
        "pdf" -> {
            val parts = payload.split('|')
            require(parts.size == 7) { "pdf needs 7 fields" }
            PdfAnnotationPlan.plan(
                dec(parts[0]), dec(parts[1]), dec(parts[2]), dec(parts[3]),
                dec(parts[4]), dec(parts[5]), dec(parts[6]),
            )
        }
        "cpio" -> {
            val bytes = hexToBytes(payload)
            val r = CpioNewcParser.parse(bytes)
            val body = r.members.joinToString("\n") { CpioNewcParser.render(it) }
            body + "\n#trailer=" + r.sawTrailer
        }
        "psd" -> {
            val bytes = hexToBytes(payload)
            PsdThumbnailInspector.render(PsdThumbnailInspector.inspect(bytes))
        }
        "odfmeta" -> OdfMetadataScrubber.scrub(dec(payload))
        "geo" -> {
            val p = segs(payload)
            when (p[0]) {
                "enc" -> {
                    require(p.size == 4) { "geo enc needs lat;lon;prec" }
                    val lat = p[1].toDoubleOrNull() ?: throw IllegalArgumentException("lat not a number")
                    val lon = p[2].toDoubleOrNull() ?: throw IllegalArgumentException("lon not a number")
                    val prec = p[3].toIntOrNull() ?: throw IllegalArgumentException("prec not an int")
                    GeohashCodec.encode(lat, lon, prec)
                }
                "dec" -> {
                    require(p.size == 2) { "geo dec needs hash" }
                    GeohashCodec.renderBox(GeohashCodec.decode(p[1]))
                }
                else -> {
                    require(p.size == 3) { "geo adj needs hash;dir" }
                    GeohashCodec.neighbor(p[1], p[2])
                }
            }
        }
        "synonym" -> {
            val p = segs(payload)
            require(p.size == 2) { "synonym needs rings~query" }
            QuerySynonymRingExpander.expand(p[0], p[1])
        }
        "termurl" -> TerminalUrlDetector.find(dec(payload))
        "notetag" -> {
            val p = segs(payload)
            require(p.size == 3) { "notetag needs corpus~op~arg" }
            val corpus = if (p[0].isEmpty()) emptyList() else NoteTagHierarchy.parseCorpus(p[0].split(','))
            when (p[1]) {
                "anc" -> NoteTagHierarchy.ancestors(corpus, p[2])
                "desc" -> NoteTagHierarchy.descendants(corpus, p[2])
                else -> NoteTagHierarchy.validate(corpus)
            }
        }
        "editlog" -> {
            val cut = payload.indexOf('~')
            require(cut >= 0) { "editlog needs base~ops" }
            val base = dec(payload.substring(0, cut))
            val opsRaw = payload.substring(cut + 1)
            val ops = if (opsRaw.isEmpty()) {
                emptyList()
            } else {
                opsRaw.split('|').map { token ->
                    when {
                        token.startsWith("i") -> {
                            val comma = token.indexOf(',')
                            require(comma > 1) { "insert token needs position" }
                            val pos = token.substring(1, comma).toIntOrNull()
                                ?: throw IllegalArgumentException("insert position not an int")
                            EditLogCoalescer.Op.Ins(pos, dec(token.substring(comma + 1)))
                        }
                        token.startsWith("d") -> {
                            val comma = token.indexOf(',')
                            require(comma > 1) { "delete token needs position" }
                            val pos = token.substring(1, comma).toIntOrNull()
                                ?: throw IllegalArgumentException("delete position not an int")
                            val len = token.substring(comma + 1).toIntOrNull()
                                ?: throw IllegalArgumentException("delete length not an int")
                            EditLogCoalescer.Op.Del(pos, len)
                        }
                        else -> throw IllegalArgumentException("unknown op token")
                    }
                }
            }
            EditLogCoalescer.coalesce(base, ops)
        }
        "macro" -> MacroExchangeCodec.canonicalize(dec(payload))
        "ntp" -> {
            val p = segs(payload)
            when (p[0]) {
                "fwd" -> {
                    require(p.size == 4) { "ntp fwd needs ref;sec;frac" }
                    val ref = p[1].toLongOrNull() ?: throw IllegalArgumentException("ref not an int")
                    val sec = p[2].toLongOrNull() ?: throw IllegalArgumentException("sec not an int")
                    val frac = p[3].toLongOrNull() ?: throw IllegalArgumentException("frac not an int")
                    NtpTimestampCodec.toEpoch(ref, sec, frac).toString()
                }
                else -> {
                    require(p.size == 2) { "ntp rev needs ms" }
                    val ms = p[1].toLongOrNull() ?: throw IllegalArgumentException("ms not an int")
                    NtpTimestampCodec.renderPair(NtpTimestampCodec.fromEpoch(ms))
                }
            }
        }
        "dur" -> DurationPhraseParser.parse(dec(payload)).toString()
        "shuffle" -> {
            val p = segs(payload)
            require(p.size == 3) { "shuffle needs seed~window~ids" }
            val window = p[1].toIntOrNull() ?: throw IllegalArgumentException("window not an int")
            val ids = if (p[2].isEmpty()) emptyList() else p[2].split(',')
            QueueShufflePlanner.plan(p[0], window, ids)
        }
        "sri" -> SubresourceIntegrityParser.parse(dec(payload))
        "oem" -> OemThrottleDetector.detect(payload)
        "pwhist" -> {
            val p = segs(payload)
            require(p.size == 3) { "pwhist needs salt~history~password" }
            PasswordHistoryHashChain.admit(p[0], p[1], p[2])
        }
        "dupframe" -> DuplicateFrameDetector.detect(payload)
        "diagexp" -> {
            // allow is a grammar field (decode = identity after transport);
            // records stay raw — the kernel pct-decodes each value itself.
            val cut = payload.indexOf('~')
            require(cut >= 0) { "diagexp needs allow~records" }
            DiagnosticExportAllowlist.export(dec(payload.substring(0, cut)),
                                             payload.substring(cut + 1))
        }
        "msgthread" -> {
            val entries = if (payload.isEmpty()) emptyList() else payload.split(',')
            val messages = entries.map { entry ->
                val f = entry.split(';')
                require(f.size == 3) { "message entry needs id;parent;text" }
                MessageThread.Companion.Message(
                    f[0],
                    if (f[1].isEmpty()) null else f[1],
                    dec(f[2]),
                )
            }
            val threads = MessageThread.empty().resolve(messages)
            threads.joinToString("\n") { t ->
                t.rootId + ":" + t.descendantIds.joinToString(",")
            }
        }
        else -> throw IllegalArgumentException("unknown feature: $feature")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: R146Checks <cases.tsv> <results.tsv>" }
        val lines = File(args[0]).readText(Charsets.UTF_8).split('\n')
        val out = StringBuilder()
        for (line in lines) {
            if (line.isEmpty() || line == "\r") continue
            val trimmed = line.removeSuffix("\r")
            val parts = trimmed.split('\t')
            require(parts.size == 3) { "case line must be id<TAB>feature<TAB>payload" }
            val id = parts[0]
            val feature = parts[1]
            val payload = parts[2]
            val verdict = try {
                dispatch(feature, payload)
            } catch (e: IllegalArgumentException) {
                "!REF"
            } catch (e: IndexOutOfBoundsException) {
                "!REF"
            } catch (e: NumberFormatException) {
                "!REF"
            } catch (e: java.util.regex.PatternSyntaxException) {
                "!REF"
            }
            out.append(id).append('\t').append(feature).append('\t').append(enc(verdict)).append('\n')
        }
        File(args[1]).writeText(out.toString(), Charsets.UTF_8)
        val count = lines.count { it.isNotEmpty() && it != "\r" }
        println("R146_PROBE_OK version=$VERSION cases=$count out=${args[1]}")
    }
}
