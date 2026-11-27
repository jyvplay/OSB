// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.privacy.DiagnosticExportAllowlist
import dev.fusion.privacy.DiagnosticExportSession
import dev.fusion.together.EditLogCoalescer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Real filesystem/stream export exercise and host-Java2D review, not Android UI. */
object R146SystemChecks {
    private var assertions = 0
    private fun verify(ok: Boolean) { check(ok); assertions++ }
    private fun refuses(action: () -> Unit) {
        try { action() } catch (_: IllegalArgumentException) { assertions++; return }
        error("expected refusal")
    }

    @JvmStatic fun main(args: Array<String>) {
        val dir = Path.of(args.single())
        Files.createDirectories(dir)
        val records = listOf("version" to "2.1-local", "mail" to "dev@example.org",
            "msg" to "bearer abc123", "path" to "/data/user/0/private/file",
            "private" to "NEVER_EXPORT_THIS", "literal" to "100%,a=b;c")
        val review = DiagnosticExportSession.prepare("version,mail,msg,path,literal", records)
        verify(review.text == "literal=100%,a=b;c\nmail=[email]\nmsg=bearer [secret]\npath=[path]\nversion=2.1-local")
        verify(!review.text.contains("NEVER_EXPORT_THIS"))
        verify(review.sha256.length == 64)
        verify(review.utf8Bytes == review.text.toByteArray().size)
        val raw = ByteArrayOutputStream()
        refuses { review.writeApproved("0".repeat(64), raw) }
        verify(raw.size() == 0)
        val newer = DiagnosticExportSession.prepare("version", listOf("version" to "changed"))
        refuses { newer.writeApproved(review.sha256, raw) }
        verify(raw.size() == 0)
        review.writeApproved(review.sha256, raw)
        val second = ByteArrayOutputStream()
        review.writeApproved(review.sha256, second)
        verify(raw.toByteArray().contentEquals(second.toByteArray()))
        Files.write(dir.resolve("diagnostics.zip"), raw.toByteArray())
        Files.writeString(dir.resolve("review.txt"), review.text)
        Files.writeString(dir.resolve("review.sha256"), review.sha256)
        var closed = false
        val callerOwned = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        review.writeApproved(review.sha256, callerOwned)
        verify(!closed)
        var failed = false
        try {
            review.writeApproved(review.sha256, object : OutputStream() {
                override fun write(b: Int) { throw java.io.IOException("forced sink failure") }
            })
        } catch (_: java.io.IOException) { failed = true }
        verify(failed)
        for (control in listOf('\n', '\r', '\t', '\u0000', '\u001b', '\u007f')) {
            refuses { DiagnosticExportSession.prepare("msg", listOf("msg" to "a${control}forged=value")) }
        }
        // Non-allowlisted values are never included; shape matching is unnecessary.
        verify(DiagnosticExportSession.prepare("", records).text == "")
        verify(DiagnosticExportAllowlist.export("literal", listOf("literal" to "%40,;=")) == "literal=%40,;=")
        refuses { DiagnosticExportSession.prepare("msg", listOf("msg" to "x", "msg" to "y")) }
        refuses { DiagnosticExportSession.prepare("BAD", records) }
        refuses { DiagnosticExportSession.prepare("msg,msg", records) }
        refuses { DiagnosticExportSession.prepare("msg", listOf("msg" to "x".repeat(1025))) }
        refuses { DiagnosticExportSession.prepare("", List(257) { "k$it" to "x" }) }
        verify(DiagnosticExportAllowlist.scrubValue("192.168.1.1 token=abc") == "[ip] token=[secret]")
        verify(DiagnosticExportAllowlist.scrubValue("[email] [ip] [path]") == "[email] [ip] [path]")
        refuses { EditLogCoalescer.coalesce("", List(513) { EditLogCoalescer.Op.Ins(0, "") }) }
        refuses { EditLogCoalescer.coalesce("", listOf(EditLogCoalescer.Op.Ins(-1, "a"))) }
        refuses { EditLogCoalescer.coalesce("a", listOf(EditLogCoalescer.Op.Del(0, 0))) }
        refuses { EditLogCoalescer.replay("a", listOf(EditLogCoalescer.Op.Del(Int.MAX_VALUE, 1))) }
        refuses { EditLogCoalescer.coalesce("a".repeat(4096), listOf(EditLogCoalescer.Op.Ins(4096, "x"))) }
        val script = "i0,${"x".repeat(256)}|i256,${"y".repeat(256)}"
        val fused = EditLogCoalescer.coalesce("", script)
        verify(fused == script) // output remains admissible on re-import
        verify(EditLogCoalescer.coalesce("", fused) == fused)
        render(review, dir.resolve("diagnostic-review.png"))
        println("R146_SYSTEM_OK assertions=$assertions real_zip=true stream_failure=true java2d=true")
    }

    private fun render(review: DiagnosticExportSession.Review, target: Path) {
        val image = BufferedImage(1100, 650, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(244, 247, 251); g.fillRect(0, 0, 1100, 650)
        g.color = Color(21, 35, 54); g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("Offline diagnostic export — exact review", 36, 56)
        g.font = Font("SansSerif", Font.PLAIN, 17)
        g.drawString("HOST Java2D • supplied records only • no network or scratchpad access", 36, 92)
        g.color = Color(255, 239, 190); g.fillRoundRect(30, 116, 1040, 68, 14, 14)
        g.color = Color(71, 43, 0)
        g.drawString("Heuristic redaction only. Review for secrets before sharing. Not anonymous.", 48, 155)
        g.color = Color.WHITE; g.fillRoundRect(30, 208, 1040, 228, 14, 14)
        g.color = Color(21, 35, 54); g.font = Font("Monospaced", Font.PLAIN, 20)
        review.text.lines().forEachIndexed { i, line ->
            verify(g.fontMetrics.stringWidth(line) < 980)
            g.drawString(line, 48, 247 + i * 32)
        }
        g.font = Font("SansSerif", Font.BOLD, 18)
        g.drawString("Approve only this exact snapshot (SHA-256):", 36, 480)
        g.font = Font("Monospaced", Font.PLAIN, 17)
        verify(g.fontMetrics.stringWidth(review.sha256) < 1010)
        g.drawString(review.sha256, 36, 514)
        g.font = Font("SansSerif", Font.PLAIN, 17)
        g.drawString("Archive: diagnostics.txt + manifest.txt • ${review.utf8Bytes} UTF-8 bytes reviewed", 36, 560)
        g.drawString("CLI export writes a NEW file only; incorrect or stale approval writes nothing.", 36, 596)
        g.dispose()
        check(ImageIO.write(image, "png", target.toFile()))
    }
}
