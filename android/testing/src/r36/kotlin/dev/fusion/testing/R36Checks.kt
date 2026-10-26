// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.calc.RpnEval
import dev.fusion.filesui.NameClash
import dev.fusion.editor.WrapCol
import dev.fusion.commands.RedirPlan
import dev.fusion.castv2.CastState
import dev.fusion.maps.ScaleBar
import dev.fusion.media.WavHead
import dev.fusion.format.image.TiffHead
import dev.fusion.privacy.KAnon
import dev.fusion.security.AttemptWindow
import dev.fusion.lens.DofNear
import dev.fusion.vault.ShannonBits
import java.io.File

/** R36 host consumer: behavioral contracts over the 12 R36 kernels. Host JVM only. */
object R36Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r36 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r36 check $n FAILED (no refusal): $label")
    }
    fun close(a: Double, b: Double, eps: Double, label: String) {
        ok(kotlin.math.abs(a - b) <= eps, "$label ($a vs $b)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fx = File(args[0])
        rpn(); names(); wrap(); redir(); cast(); scale()
        wav(File(fx, "wav.bin").readBytes())
        tiff(File(fx, "tiff.bin").readBytes())
        kanon(); window(); dof(); shannon()
        println("R36 PASS: $n production-kernel assertions across 12 kernels. " +
            "Host JVM only; not Android/device evidence.")
    }

    fun rpn() {
        ok(RpnEval.eval(listOf("3", "4", "+")) == 7.0, "add")
        ok(RpnEval.eval(listOf("10", "4", "-", "3", "/")) == 2.0, "subdiv")
        ok(RpnEval.eval(listOf("2", "3", "*", "4", "+")) == 10.0, "muladd")
        ok(RpnEval.eval(listOf("9", "sqrt")) == 3.0, "sqrt")
        ok(RpnEval.eval(listOf("5", "neg")) == -5.0, "neg")
        ok(RpnEval.eval(listOf("7", "2", "/")) == 3.5, "div")
        ok(RpnEval.eval(listOf("0.1", "0.2", "+")) == 0.30000000000000004, "ieee pin")
        ok(RpnEval.eval(listOf("-2.5", "4", "*")) == -10.0, "neg literal")
        ok(RpnEval.eval(listOf("2", "10", "neg", "+")) == -8.0, "mixed")
        ok(RpnEval.eval(listOf("15", "7", "1", "1", "+", "-", "/", "3", "*", "2", "1", "1", "+", "+", "-")) == 5.0, "classic")
        ok(RpnEval.eval(listOf("4", "2", "/", "3", "*")) == 6.0, "chain")
        fails("div zero") { RpnEval.eval(listOf("1", "0", "/")) }
        fails("underflow") { RpnEval.eval(listOf("1", "+")) }
        fails("unknown op") { RpnEval.eval(listOf("1", "2", "^")) }
        fails("bad literal") { RpnEval.eval(listOf("abc")) }
        fails("nan literal") { RpnEval.eval(listOf("NaN")) }
        fails("inf literal") { RpnEval.eval(listOf("Infinity")) }
        fails("leftover") { RpnEval.eval(listOf("1", "2")) }
        fails("sqrt neg") { RpnEval.eval(listOf("-4", "sqrt")) }
        fails("overflow") { RpnEval.eval(listOf("1e308", "10", "*")) }
        fails("empty") { RpnEval.eval(emptyList()) }
    }

    fun names() {
        ok(NameClash.nextName(setOf("b.txt"), "a.txt") == "a.txt", "free")
        ok(NameClash.nextName(setOf("a.txt"), "a.txt") == "a (2).txt", "first clash")
        ok(NameClash.nextName(setOf("a.txt", "a (2).txt"), "a.txt") == "a (3).txt", "second clash")
        ok(NameClash.nextName(setOf("a.txt", "a (3).txt"), "a.txt") == "a (2).txt", "gap fill")
        ok(NameClash.nextName(setOf("README"), "README") == "README (2)", "no ext")
        ok(NameClash.nextName(setOf("a (2).txt"), "a (2).txt") == "a (3).txt", "numbered input reuses base")
        ok(NameClash.nextName(setOf(".gitignore"), ".gitignore") == ".gitignore (2)", "dotfile")
        ok(NameClash.nextName(setOf("a.tar.gz"), "a.tar.gz") == "a.tar (2).gz", "multi dot")
        ok(NameClash.nextName(setOf("P.JPG"), "p.jpg") == "p.jpg", "case sensitive")
        ok(NameClash.nextName(setOf("n"), "n") == "n (2)", "single char")
        fails("blank") { NameClash.nextName(emptySet(), "  ") }
    }

    fun wrap() {
        ok(WrapCol.wrap("a bb ccc", 6) == listOf("a bb", "ccc"), "greedy")
        ok(WrapCol.wrap("ab cd", 5) == listOf("ab cd"), "exact fit")
        ok(WrapCol.wrap("hello", 10) == listOf("hello"), "single")
        ok(WrapCol.wrap("a b", 1) == listOf("a", "b"), "width one")
        ok(WrapCol.wrap("", 10).isEmpty(), "empty")
        ok(WrapCol.wrap("   ", 10).isEmpty(), "blank")
        ok(WrapCol.wrap("a  b", 5) == listOf("a b"), "space collapse")
        ok(WrapCol.wrap("one two three four", 9) == listOf("one two", "three", "four"), "cascade")
        ok(WrapCol.wrap("aa bb cc dd", 5) == listOf("aa bb", "cc dd"), "pairs")
        fails("word over") { WrapCol.wrap("toolong", 3) }
        fails("newline") { WrapCol.wrap("a\nb", 10) }
        fails("width zero") { WrapCol.wrap("a", 0) }
        fails("width over") { WrapCol.wrap("a", 1001) }
    }

    fun redir() {
        val plain = RedirPlan.parse(listOf("ls", "-l"))
        ok(plain.argv == listOf("ls", "-l") && plain.redirs.isEmpty(), "plain")
        val out = RedirPlan.parse(listOf("cat", "f", ">", "o"))
        ok(out.argv == listOf("cat", "f"), "out argv")
        ok(out.redirs == listOf(RedirPlan.Redir(RedirPlan.Stream.STDOUT, false, "o")), "out redir")
        val app = RedirPlan.parse(listOf("echo", "x", ">>", "log"))
        ok(app.redirs == listOf(RedirPlan.Redir(RedirPlan.Stream.STDOUT, true, "log")), "append")
        val inp = RedirPlan.parse(listOf("sort", "<", "in"))
        ok(inp.redirs == listOf(RedirPlan.Redir(RedirPlan.Stream.STDIN, false, "in")), "stdin")
        val err = RedirPlan.parse(listOf("cmd", "2>", "err"))
        ok(err.redirs == listOf(RedirPlan.Redir(RedirPlan.Stream.STDERR, false, "err")), "stderr")
        val multi = RedirPlan.parse(listOf("a", "<", "i", ">", "o", "2>", "e"))
        ok(multi.argv == listOf("a"), "multi argv")
        ok(multi.redirs.map { it.stream } == listOf(RedirPlan.Stream.STDIN, RedirPlan.Stream.STDOUT, RedirPlan.Stream.STDERR), "multi order")
        val swapped = RedirPlan.parse(listOf("cmd", "2>", "e", ">", "o"))
        ok(swapped.redirs.map { it.stream } == listOf(RedirPlan.Stream.STDERR, RedirPlan.Stream.STDOUT), "source order kept")
        val glued = RedirPlan.parse(listOf("cmd", "2>file"))
        ok(glued.argv == listOf("cmd", "2>file") && glued.redirs.isEmpty(), "glued stays arg")
        fails("trailing op") { RedirPlan.parse(listOf("a", ">")) }
        fails("op target") { RedirPlan.parse(listOf("a", ">", "<")) }
        fails("dup stdout") { RedirPlan.parse(listOf("a", ">", "x", ">", "y")) }
        fails("dup stdin") { RedirPlan.parse(listOf("a", "<", "x", "<", "y")) }
        fails("dup stderr") { RedirPlan.parse(listOf("a", "2>", "x", "2>", "y")) }
        fails("empty target") { RedirPlan.parse(listOf("a", ">", "")) }
    }

    fun cast() {
        ok(CastState.parse("IDLE") == CastState.State.IDLE, "parse idle")
        ok(CastState.parse("BUFFERING") == CastState.State.BUFFERING, "parse buffering")
        ok(CastState.parse("PLAYING") == CastState.State.PLAYING, "parse playing")
        ok(CastState.parse("PAUSED") == CastState.State.PAUSED, "parse paused")
        ok(CastState.next(CastState.State.IDLE, CastState.Event.LOAD) == CastState.State.BUFFERING, "idle load")
        ok(CastState.next(CastState.State.IDLE, CastState.Event.PLAY) == CastState.State.PLAYING, "idle play")
        ok(CastState.next(CastState.State.BUFFERING, CastState.Event.BUFFER_OK) == CastState.State.PLAYING, "buf ok")
        ok(CastState.next(CastState.State.BUFFERING, CastState.Event.STOP) == CastState.State.IDLE, "buf stop")
        ok(CastState.next(CastState.State.PLAYING, CastState.Event.PAUSE) == CastState.State.PAUSED, "playing pause")
        ok(CastState.next(CastState.State.PLAYING, CastState.Event.LOAD) == CastState.State.BUFFERING, "playing load")
        ok(CastState.next(CastState.State.PLAYING, CastState.Event.STOP) == CastState.State.IDLE, "playing stop")
        ok(CastState.next(CastState.State.PLAYING, CastState.Event.FINISH) == CastState.State.IDLE, "playing finish")
        ok(CastState.next(CastState.State.PAUSED, CastState.Event.PLAY) == CastState.State.PLAYING, "paused play")
        ok(CastState.next(CastState.State.PAUSED, CastState.Event.LOAD) == CastState.State.BUFFERING, "paused load")
        ok(CastState.next(CastState.State.PAUSED, CastState.Event.STOP) == CastState.State.IDLE, "paused stop")
        fails("idle pause") { CastState.next(CastState.State.IDLE, CastState.Event.PAUSE) }
        fails("idle stop") { CastState.next(CastState.State.IDLE, CastState.Event.STOP) }
        fails("idle finish") { CastState.next(CastState.State.IDLE, CastState.Event.FINISH) }
        fails("idle bufok") { CastState.next(CastState.State.IDLE, CastState.Event.BUFFER_OK) }
        fails("buf pause") { CastState.next(CastState.State.BUFFERING, CastState.Event.PAUSE) }
        fails("buf play") { CastState.next(CastState.State.BUFFERING, CastState.Event.PLAY) }
        fails("buf load") { CastState.next(CastState.State.BUFFERING, CastState.Event.LOAD) }
        fails("paused pause") { CastState.next(CastState.State.PAUSED, CastState.Event.PAUSE) }
        fails("paused finish") { CastState.next(CastState.State.PAUSED, CastState.Event.FINISH) }
        fails("playing play") { CastState.next(CastState.State.PLAYING, CastState.Event.PLAY) }
        fails("lower idle") { CastState.parse("idle") }
        fails("stopped") { CastState.parse("STOPPED") }
    }

    fun scale() {
        ok(ScaleBar.metersPerPixel(0.0, 0) == 156543.03392, "equator z0")
        ok(ScaleBar.metersPerPixel(0.0, 1) == 78271.51696, "equator z1")
        close(ScaleBar.metersPerPixel(45.0, 10), 108.09828206283957, 1e-9, "mpp 45/10")
        val b1 = ScaleBar.bar(1.0, 100.0)
        ok(b1.meters == 100.0 && b1.pixels == 100.0, "bar exact")
        val b2 = ScaleBar.bar(1.0, 250.0)
        ok(b2.meters == 200.0 && b2.pixels == 200.0, "bar two")
        val b3 = ScaleBar.bar(156543.03392, 100.0)
        ok(b3.meters == 1.0e7, "bar meters")
        close(b3.pixels, 63.8801979851139, 1e-9, "bar pixels")
        val b4 = ScaleBar.bar(0.5, 3.0)
        ok(b4.meters == 1.0 && b4.pixels == 2.0, "bar small")
        val b5 = ScaleBar.bar(10.0, 50.0)
        ok(b5.meters == 500.0 && b5.pixels == 50.0, "bar mid")
        fails("lat over") { ScaleBar.metersPerPixel(90.0, 5) }
        fails("lat under") { ScaleBar.metersPerPixel(-86.0, 5) }
        fails("zoom high") { ScaleBar.metersPerPixel(0.0, 20) }
        fails("zoom neg") { ScaleBar.metersPerPixel(0.0, -1) }
        fails("bad mpp") { ScaleBar.bar(0.0, 10.0) }
        fails("bad target") { ScaleBar.bar(1.0, -5.0) }
    }

    fun wav(bytes: ByteArray) {
        val h = WavHead.parse(bytes)
        ok(h.format == 1 && h.channels == 1, "fixture format")
        ok(h.sampleRate == 8000, "fixture rate")
        ok(h.byteRate == 16000 && h.blockAlign == 2 && h.bitsPerSample == 16, "fixture geometry")
        ok(h.riffSize == 36, "fixture riff")
        val stereo = ByteArray(44)
        fun tag(off: Int, s: String) { for (i in s.indices) stereo[off + i] = s[i].code.toByte() }
        fun le32(off: Int, v: Int) { for (i in 0..3) stereo[off + i] = (v ushr (8 * i)).toByte() }
        fun le16(off: Int, v: Int) { stereo[off] = v.toByte(); stereo[off + 1] = (v ushr 8).toByte() }
        tag(0, "RIFF"); le32(4, 40); tag(8, "WAVE"); tag(12, "fmt "); le32(16, 16)
        le16(20, 3); le16(22, 2); le32(24, 44100); le32(28, 352800); le16(32, 8); le16(34, 32)
        tag(36, "data"); le32(40, 0)
        val f = WavHead.parse(stereo)
        ok(f.format == 3 && f.channels == 2, "float stereo")
        ok(f.sampleRate == 44100 && f.byteRate == 352800, "float rate")
        val sized = bytes.copyOf().also { it[4] = 0xD2.toByte(); it[5] = 4; it[6] = 0; it[7] = 0 }
        ok(WavHead.parse(sized).riffSize == 1234, "riff echo")
        fails("short") { WavHead.parse(ByteArray(39)) }
        fails("riff") { WavHead.parse(bytes.copyOf().also { it[0] = 'R'.code.toByte(); it[1] = 'I'.code.toByte(); it[2] = 'C'.code.toByte() }) }
        fails("wave") { WavHead.parse(bytes.copyOf().also { it[8] = 'X'.code.toByte() }) }
        fails("fmt") { WavHead.parse(bytes.copyOf().also { it[12] = 'x'.code.toByte() }) }
        fails("fmtlen") { WavHead.parse(bytes.copyOf().also { it[16] = 8 }) }
        fails("adpcm") { WavHead.parse(bytes.copyOf().also { it[20] = 2 }) }
        fails("channels") { WavHead.parse(bytes.copyOf().also { it[22] = 0 }) }
        fails("rate") { WavHead.parse(bytes.copyOf().also { it[24] = 0; it[25] = 0; it[26] = 0; it[27] = 0 }) }
        fails("bits") { WavHead.parse(bytes.copyOf().also { it[34] = 20 }) }
        fails("align") { WavHead.parse(bytes.copyOf().also { it[32] = 4 }) }
        fails("byterate") { WavHead.parse(bytes.copyOf().also { it[28] = 1 }) }
    }

    fun tiff(bytes: ByteArray) {
        val h = TiffHead.parse(bytes)
        ok(h.littleEndian && h.ifdOffset == 8L, "fixture le")
        val be = TiffHead.parse(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 16))
        ok(!be.littleEndian && be.ifdOffset == 16L, "be 16")
        val le = TiffHead.parse(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 10, 0, 0, 0))
        ok(le.littleEndian && le.ifdOffset == 10L, "le 10")
        val beMin = TiffHead.parse(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8))
        ok(!beMin.littleEndian && beMin.ifdOffset == 8L, "be minimal")
        fails("short") { TiffHead.parse(ByteArray(7)) }
        fails("order") { TiffHead.parse(byteArrayOf('X'.code.toByte(), 'Y'.code.toByte(), 42, 0, 8, 0, 0, 0)) }
        fails("le magic be") { TiffHead.parse(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 0, 42, 8, 0, 0, 0)) }
        fails("be magic le") { TiffHead.parse(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 42, 0, 0, 0, 0, 8)) }
        fails("odd off") { TiffHead.parse(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 9, 0, 0, 0)) }
        fails("low off") { TiffHead.parse(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 4, 0, 0, 0)) }
        fails("bigtiff") { TiffHead.parse(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 43, 0, 8, 0, 0, 0)) }
    }

    fun kanon() {
        val rows = listOf(listOf("a", "x"), listOf("a", "x"), listOf("b", "y"))
        val a = KAnon.audit(rows, 2)
        ok(a.groups == 2 && a.minSize == 1, "groups min")
        ok(!a.ok, "not ok")
        ok(a.violations == listOf(KAnon.Violation(listOf("b", "y"), 1)), "violation pin")
        val v1 = KAnon.audit(rows, 1)
        ok(v1.ok && v1.violations.isEmpty(), "k1 vacuous")
        val pairs = listOf(listOf("a"), listOf("a"), listOf("b"), listOf("b"))
        val p = KAnon.audit(pairs, 2)
        ok(p.ok && p.minSize == 2 && p.groups == 2, "pairs ok")
        val one = KAnon.audit(List(4) { listOf("z") }, 4)
        ok(one.ok && one.groups == 1 && one.minSize == 4, "single group")
        val two = KAnon.audit(listOf(listOf("a"), listOf("b"), listOf("c")), 2)
        ok(two.violations.map { it.key } == listOf(listOf("a"), listOf("b"), listOf("c")), "violation order")
        fails("k zero") { KAnon.audit(rows, 0) }
        fails("empty") { KAnon.audit(emptyList(), 2) }
        fails("ragged") { KAnon.audit(listOf(listOf("a"), listOf("a", "b")), 2) }
        fails("no cols") { KAnon.audit(listOf(emptyList(), emptyList()), 2) }
        fails("over cap") { KAnon.audit(List(100001) { listOf("a") }, 2) }
    }

    fun window() {
        ok(AttemptWindow.allows(emptyList(), 100, 3, 60), "empty admits")
        ok(!AttemptWindow.allows(listOf(50, 60, 70), 100, 3, 60), "three denies")
        ok(AttemptWindow.allows(listOf(50, 60), 100, 3, 60), "two admits")
        ok(AttemptWindow.allows(listOf(40), 100, 1, 60), "boundary expires")
        ok(AttemptWindow.allows(listOf(150), 100, 1, 60), "future ignored")
        ok(!AttemptWindow.allows(listOf(70, 50), 100, 2, 60), "unsorted counts")
        ok(AttemptWindow.allows(listOf(10, 20), 100, 2, 60), "old expired")
        ok(!AttemptWindow.allows(listOf(100), 100, 1, 60), "now counts")
        ok(AttemptWindow.allows(List(100) { 1L }, 1000, 5, 60), "many expired")
        fails("neg now") { AttemptWindow.allows(emptyList(), -1, 1, 60) }
        fails("bad max") { AttemptWindow.allows(emptyList(), 1, 0, 60) }
        fails("bad window") { AttemptWindow.allows(emptyList(), 1, 1, 0) }
        fails("neg prior") { AttemptWindow.allows(listOf(-5), 100, 2, 60) }
        fails("over cap") { AttemptWindow.allows(List(100001) { 1L }, 100, 2, 60) }
    }

    fun dof() {
        close(DofNear.hyperfocal(50.0, 2.8, 0.03), 29811.904761904763, 1e-6, "hyperfocal")
        val l = DofNear.limits(50.0, 2.8, 0.03, 5000.0)
        close(l.near, 4286.988133616846, 1e-6, "near")
        close(l.far!!, 5997.505037904232, 1e-6, "far")
        ok(l.near < 5000.0 && l.far!! > 5000.0, "bracket")
        val inf = DofNear.limits(50.0, 2.8, 0.03, 29811.904761904763)
        ok(inf.far == null, "far infinity")
        ok(inf.near > 0.0, "near finite")
        fails("zero focal") { DofNear.hyperfocal(0.0, 2.8, 0.03) }
        fails("neg aperture") { DofNear.hyperfocal(50.0, -1.0, 0.03) }
        fails("nan coc") { DofNear.hyperfocal(50.0, 2.8, Double.NaN) }
        fails("zero focus") { DofNear.limits(50.0, 2.8, 0.03, 0.0) }
    }

    fun shannon() {
        ok(ShannonBits.bitsPerSymbol(byteArrayOf(0, 0, 0, 0)) == 0.0, "single symbol")
        ok(ShannonBits.bitsPerSymbol(byteArrayOf(0, 1)) == 1.0, "fair coin")
        ok(ShannonBits.bitsPerSymbol(byteArrayOf(0, 1, 2, 3)) == 2.0, "four symbols")
        ok(ShannonBits.totalBits(byteArrayOf(0, 1)) == 2.0, "total bits")
        close(ShannonBits.bitsPerSymbol(byteArrayOf(0, 0, 0, 1)), 0.8112781244591328, 1e-12, "skewed")
        close(ShannonBits.totalBits(byteArrayOf(0, 0, 0, 1)), 3.2451124978365313, 1e-12, "skewed total")
        fails("empty") { ShannonBits.bitsPerSymbol(ByteArray(0)) }
        fails("over cap") { ShannonBits.bitsPerSymbol(ByteArray(1048577)) }
    }
}
