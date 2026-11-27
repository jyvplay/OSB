// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.security.diagnostics.DiagnosticSaveWorkflow
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Actual protocol snapshots rendered on host Java2D. NOT an Android UI screenshot. */
object R176ReviewFrame {
    @JvmStatic fun main(args: Array<String>) {
        val dir = Path.of(args.single()); Files.createDirectories(dir)
        val workflow = DiagnosticSaveWorkflow()
        workflow.prepare("app.version,operator.note", listOf("app.version" to "2.1-local", "operator.note" to "dev@example.org"))
        val reviewed = workflow.snapshot()
        val ticket = workflow.beginSave(requireNotNull(reviewed.sha256))
        val chosen = dir.resolve("frame-review.zip")
        check(workflow.save(ticket, object : DiagnosticSaveWorkflow.Destination {
            override fun openOutput() = Files.newOutputStream(chosen)
            override fun openInput() = Files.newInputStream(chosen)
        }))
        val saved = workflow.snapshot()
        val image = BufferedImage(1200, 720, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(244, 247, 251); g.fillRect(0, 0, image.width, image.height)
        g.color = Color(20, 39, 61); g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("Diagnostic export — review and verified readback", 32, 52)
        g.font = Font("SansSerif", Font.PLAIN, 17)
        g.drawString("HOST Java2D protocol artifact. Not an Android screenshot or device acceptance.", 32, 88)
        fun line(value: String, y: Int) {
            check(g.fontMetrics.stringWidth(value) < 1120) { "host artifact clips text" }
            g.drawString(value, 40, y)
        }
        g.color = Color(255, 240, 191); g.fillRoundRect(26, 112, 1148, 88, 16, 16)
        g.color = Color(64, 43, 0)
        line("Heuristic redaction only. Review for secrets before sharing. Not anonymous.", 145)
        line("A destination provider may upload the ZIP. Choose a trusted local provider if needed.", 178)
        g.color = Color.WHITE; g.fillRoundRect(26, 222, 1148, 200, 16, 16)
        g.color = Color(20, 39, 61); g.font = Font("SansSerif", Font.BOLD, 19)
        line("Actual immutable REVIEW snapshot — ${reviewed.utf8Bytes} UTF-8 bytes", 254)
        g.font = Font("Monospaced", Font.PLAIN, 20)
        reviewed.review!!.lines().forEachIndexed { index, text -> line(text, 294 + index * 32) }
        g.font = Font("Monospaced", Font.PLAIN, 16)
        line("SHA-256: ${reviewed.sha256}", 389)
        g.font = Font("SansSerif", Font.BOLD, 20)
        line("Observed terminal phase: ${saved.phase}", 473)
        g.font = Font("SansSerif", Font.PLAIN, 16)
        line(saved.message, 508)
        line("Exact ZIP written to disk, closed, reopened and compared byte-for-byte by hash.", 542)
        line("Review payload and digest retained after completion: ${saved.review != null || saved.sha256 != null}", 576)
        line("Entry names: diagnostics.txt + manifest.txt. Independent Python ZIP verification also runs.", 610)
        line("No scratchpad read. No network. No new permission. No full-spec Class A claim.", 660)
        g.dispose()
        check(ImageIO.write(image, "png", dir.resolve("diagnostic-protocol.png").toFile()))
        println("R176_HOST_FRAME_OK actual_phases=${reviewed.phase},${saved.phase} android_ui=false")
    }
}
