// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.calc.Fraction
import dev.fusion.calc.FractionApprox
import dev.fusion.lok.OfficeCapability
import dev.fusion.castv2.CastMediaMeta
import dev.fusion.castv2.MediaMeta
import dev.fusion.commands.ArgSplit
import dev.fusion.security.PinRules
import dev.fusion.privacy.AgedRecord
import dev.fusion.privacy.RetentionSweep
import dev.fusion.continuity.ICalDuration
import dev.fusion.maps.GeoPoint
import dev.fusion.maps.GeoUri
import dev.fusion.vault.LockState
import dev.fusion.vault.LockoutPolicy
import dev.fusion.media.CueSheet
import dev.fusion.media.CueSheetParser
import dev.fusion.media.CueTrack
import java.io.File
import java.math.BigDecimal

/** R32 host consumer: behavioral contracts over the 10 R32 kernels. Host JVM only. */
object R32Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r32 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r32 check $n FAILED (no refusal): $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args[0])
        fraction(); office(); castmeta(); argsplit(); pin(); sweep(); duration(); geo()
        lockout()
        cue(File(dir, "sheet.cue").readText())
        println("R32 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    fun fraction() {
        ok(FractionApprox.approx(BigDecimal("0.5"), 100) == Fraction(1, 2), "half exact")
        ok(FractionApprox.approx(BigDecimal("3.14159"), 10) == Fraction(22, 7), "pi to 22/7")
        ok(FractionApprox.approx(BigDecimal("3.14159"), 100) == Fraction(311, 99), "pi semiconvergent")
        ok(FractionApprox.approx(BigDecimal("0.333333"), 100) == Fraction(1, 3), "third")
        ok(FractionApprox.approx(BigDecimal("-2.5"), 10) == Fraction(-5, 2), "negative")
        ok(FractionApprox.approx(BigDecimal.ZERO, 10) == Fraction(0, 1), "zero")
        ok(FractionApprox.approx(BigDecimal("7"), 1) == Fraction(7, 1), "integer")
        ok(FractionApprox.approx(BigDecimal("0.125"), 1000) == Fraction(1, 8), "eighth exact")
        ok(Fraction.of(4, -6) == Fraction(-2, 3), "normalize sign")
        ok(Fraction.of(-4, -6) == Fraction(2, 3), "normalize both")
        fails("maxDen 0") { FractionApprox.approx(BigDecimal.ONE, 0) }
        fails("maxDen over") { FractionApprox.approx(BigDecimal.ONE, 1_000_001) }
        fails("value over") { FractionApprox.approx(BigDecimal("1000000000001"), 100) }
        fails("zero den") { Fraction.of(1, 0) }
    }

    fun office() {
        val pdf = OfficeCapability.support("pdf")
        ok(pdf.view && !pdf.edit && pdf.gates.size == 6, "pdf view-only")
        ok(OfficeCapability.support("ODT").format == "odt", "case folds")
        ok(OfficeCapability.support(".pdf").view, "dot stripped")
        ok(!OfficeCapability.support("docx").view && OfficeCapability.support("docx").gates.contains("engine payload"), "docx gated")
        ok(OfficeCapability.support("txt").view && !OfficeCapability.support("txt").edit, "txt view-only")
        ok(OfficeCapability.viewable() == listOf("csv", "pdf", "txt"), "viewable sorted")
        val unknown = OfficeCapability.support("xyz")
        ok(!unknown.view && !unknown.edit && unknown.gates == listOf("format not in bounded matrix"), "unknown probes negative")
        ok(OfficeCapability.support("xlsx").gates.contains("license"), "license gate named")
        fails("empty id") { OfficeCapability.support("") }
        fails("space id") { OfficeCapability.support("o dt") }
        fails("long id") { OfficeCapability.support("a".repeat(17)) }
    }

    fun castmeta() {
        val m = CastMediaMeta.validate("http://192.168.1.5/v.mp4", "video/mp4", "Clip")
        ok(m == MediaMeta("http://192.168.1.5/v.mp4", "video/mp4", "Clip"), "valid record")
        ok(CastMediaMeta.validate("https://h/a?b=c&d=e", "video/mp4", "t").url.endsWith("?b=c&d=e"), "query kept")
        fails("ftp scheme") { CastMediaMeta.validate("ftp://h/a", "video/mp4", "t") }
        fails("bare host") { CastMediaMeta.validate("http://", "video/mp4", "t") }
        fails("space url") { CastMediaMeta.validate("http://h/a b", "video/mp4", "t") }
        fails("upper scheme") { CastMediaMeta.validate("HTTP://h/a", "video/mp4", "t") }
        fails("mime params") { CastMediaMeta.validate("http://h/a", "text/html;level=1", "t") }
        fails("upper mime") { CastMediaMeta.validate("http://h/a", "Video/MP4", "t") }
        fails("bare mime") { CastMediaMeta.validate("http://h/a", "video", "t") }
        fails("empty title") { CastMediaMeta.validate("http://h/a", "video/mp4", "") }
        fails("long title") { CastMediaMeta.validate("http://h/a", "video/mp4", "t".repeat(257)) }
        fails("quote title") { CastMediaMeta.validate("http://h/a", "video/mp4", "say \"hi\"") }
        fails("backslash title") { CastMediaMeta.validate("http://h/a", "video/mp4", "a\\b") }
        fails("control title") { CastMediaMeta.validate("http://h/a", "video/mp4", "a\tb") }
    }

    fun argsplit() {
        ok(ArgSplit.split("a b c") == listOf("a", "b", "c"), "basic")
        ok(ArgSplit.split("  a\tb  ") == listOf("a", "b"), "whitespace")
        ok(ArgSplit.split("a \"b c\" d") == listOf("a", "b c", "d"), "double quotes")
        ok(ArgSplit.split("a 'b c' d") == listOf("a", "b c", "d"), "single quotes")
        ok(ArgSplit.split("'a\"b'") == listOf("a\"b"), "single literal dquote")
        ok(ArgSplit.split("\"a'b\"") == listOf("a'b"), "double literal squote")
        ok(ArgSplit.split("a\\ b") == listOf("a b"), "escaped space")
        ok(ArgSplit.split("\"a\\\"b\"") == listOf("a\"b"), "escaped dquote")
        ok(ArgSplit.split("\"\"") == listOf(""), "empty dquoted arg")
        ok(ArgSplit.split("a \"\" b") == listOf("a", "", "b"), "empty mid arg")
        ok(ArgSplit.split("a # comment") == listOf("a"), "comment")
        ok(ArgSplit.split("# only") == emptyList<String>(), "only comment")
        ok(ArgSplit.split("\"a#b\"") == listOf("a#b"), "hash in quotes")
        ok(ArgSplit.split("ab#cd") == listOf("ab"), "hash ends arg")
        ok(ArgSplit.split("") == emptyList<String>(), "empty line")
        ok(ArgSplit.split("\"a\"bonus") == listOf("abonus"), "quote join")
        fails("unterminated double") { ArgSplit.split("\"abc") }
        fails("unterminated single") { ArgSplit.split("ab'cd") }
        fails("trailing backslash") { ArgSplit.split("ab\\") }
        fails("trailing backslash quotes") { ArgSplit.split("\"ab\\") }
        fails("over chars") { ArgSplit.split("a".repeat(8193)) }
        fails("over args") { ArgSplit.split((1..257).joinToString(" ") { "a" }) }
    }

    fun pin() {
        ok(PinRules.evaluate("1357").ok, "1357 ok")
        ok(PinRules.evaluate("2749").ok, "2749 ok")
        ok(PinRules.evaluate("1234").reasons.contains("straight run"), "1234 run")
        ok(PinRules.evaluate("1234").reasons.contains("blocklisted"), "1234 blocked")
        ok(!PinRules.evaluate("1234").ok, "1234 bad")
        ok(PinRules.evaluate("0000").reasons.contains("all same digit"), "0000 same")
        ok(!PinRules.evaluate("0000").reasons.contains("straight run"), "0000 not run")
        ok(PinRules.evaluate("7890").reasons.contains("straight run"), "7890 wrap run")
        ok(PinRules.evaluate("2109").reasons.contains("straight run"), "2109 down wrap")
        ok(PinRules.evaluate("5432").reasons.contains("straight run"), "5432 down")
        ok(PinRules.evaluate("1212").reasons.contains("repeated half"), "1212 half")
        ok(PinRules.evaluate("12ab").reasons == listOf("not all digits"), "alpha single reason")
        ok(PinRules.evaluate("123").reasons.contains("length 3 outside 4..12"), "short")
        ok(PinRules.evaluate("1".repeat(13)).reasons.contains("length 13 outside 4..12"), "long")
        ok(PinRules.evaluate("").reasons.contains("length 0 outside 4..12"), "empty length")
        ok(!PinRules.evaluate("").reasons.contains("all same digit"), "empty not same")
    }

    fun sweep() {
        val table = mapOf("log" to 1000L, "consent" to 5000L)
        val recs = listOf(
            AgedRecord("a", "log", 9000),
            AgedRecord("b", "log", 8999),
            AgedRecord("c", "consent", 5000),
            AgedRecord("d", "consent", 4999),
        )
        val s = RetentionSweep.sweep(recs, table, 10000)
        ok(s.keep == listOf("a", "c"), "keep boundary inclusive")
        ok(s.purge == listOf("b", "d"), "purge strictly older")
        ok(RetentionSweep.sweep(emptyList(), table, 10000).keep.isEmpty(), "empty sweep")
        fails("unknown kind") { RetentionSweep.sweep(listOf(AgedRecord("x", "nope", 1)), table, 10) }
        fails("future stamp") { RetentionSweep.sweep(listOf(AgedRecord("x", "log", 11)), table, 10) }
        fails("negative now") { RetentionSweep.sweep(recs, table, -1) }
        fails("empty table") { RetentionSweep.sweep(recs, emptyMap(), 10000) }
        fails("zero age") { RetentionSweep.sweep(recs, mapOf("log" to 0), 10000) }
        fails("bad id") { RetentionSweep.sweep(listOf(AgedRecord("A!", "log", 1)), table, 10) }
        fails("over records") {
            RetentionSweep.sweep(List(100001) { AgedRecord("r$it", "log", 1) }, table, 10)
        }
    }

    fun duration() {
        ok(ICalDuration.toSeconds("P1W") == 604800L, "week")
        ok(ICalDuration.toSeconds("P1DT2H3M4S") == 93784L, "full")
        ok(ICalDuration.toSeconds("-PT15M") == -900L, "negative")
        ok(ICalDuration.toSeconds("PT0S") == 0L, "zero")
        ok(ICalDuration.toSeconds("+P2D") == 172800L, "plus")
        ok(ICalDuration.toSeconds("PT1H30M") == 5400L, "hm")
        ok(ICalDuration.toSeconds("P0D") == 0L, "zero days")
        fails("month") { ICalDuration.toSeconds("P1M") }
        fails("year") { ICalDuration.toSeconds("P1Y") }
        fails("bare P") { ICalDuration.toSeconds("P") }
        fails("bare PT") { ICalDuration.toSeconds("PT") }
        fails("bare PDT") { ICalDuration.toSeconds("P1DT") }
        fails("order") { ICalDuration.toSeconds("PT1S2H") }
        fails("dup part") { ICalDuration.toSeconds("PT1H2H") }
        fails("week plus") { ICalDuration.toSeconds("P1W2D") }
        fails("no P") { ICalDuration.toSeconds("T1H") }
        fails("space") { ICalDuration.toSeconds("P1D ") }
        fails("ten digits") { ICalDuration.toSeconds("P0000000001D") }
        fails("over cap") { ICalDuration.toSeconds("P4000D") }
        fails("empty") { ICalDuration.toSeconds("") }
    }

    fun geo() {
        ok(GeoUri.parse("geo:48.2,16.3") == GeoPoint(48.2, 16.3, null, null), "basic")
        ok(GeoUri.parse("GEO:-33.86,151.20,12.5") == GeoPoint(-33.86, 151.2, 12.5, null), "upper + alt")
        ok(GeoUri.parse("geo:48.2,16.3;crs=wgs84") == GeoPoint(48.2, 16.3, null, null), "crs ok")
        ok(GeoUri.parse("geo:48.2,16.3;u=30") == GeoPoint(48.2, 16.3, null, 30.0), "unc ok")
        ok(GeoUri.parse("geo:48.2,16.3;crs=WGS84;u=30.5") == GeoPoint(48.2, 16.3, null, 30.5), "both params")
        ok(GeoUri.format(GeoPoint(48.2, 16.3, null, null)) == "geo:48.2,16.3", "format basic")
        ok(GeoUri.parse(GeoUri.format(GeoPoint(-33.86, 151.2, 12.5, 30.0))) == GeoPoint(-33.86, 151.2, 12.5, 30.0), "round trip")
        fails("no scheme") { GeoUri.parse("48.2,16.3") }
        fails("lat over") { GeoUri.parse("geo:90.1,0") }
        fails("lon over") { GeoUri.parse("geo:0,180.1") }
        fails("one coord") { GeoUri.parse("geo:48.2") }
        fails("four coords") { GeoUri.parse("geo:1,2,3,4") }
        fails("bad crs") { GeoUri.parse("geo:1,2;crs=luna") }
        fails("unknown param") { GeoUri.parse("geo:1,2;foo=bar") }
        fails("bad param") { GeoUri.parse("geo:1,2;u") }
        fails("neg unc") { GeoUri.parse("geo:1,2;u=-1") }
        fails("nan") { GeoUri.parse("geo:NaN,0") }
        fails("space") { GeoUri.parse("geo:48.2, 16.3") }
        fails("format range") { GeoUri.format(GeoPoint(91.0, 0.0, null, null)) }
    }

    fun lockout() {
        val s0 = LockState(0, 0)
        val s1 = LockoutPolicy.record(s0, 1000, false)
        ok(s1 == LockState(1, 0), "first free")
        val s3 = LockoutPolicy.record(LockoutPolicy.record(s1, 1000, false), 1000, false)
        ok(s3 == LockState(3, 0), "third free")
        val s4 = LockoutPolicy.record(s3, 10000, false)
        ok(s4 == LockState(4, 40000), "fourth 30s")
        ok(LockoutPolicy.locked(s4, 39999), "locked before")
        ok(!LockoutPolicy.locked(s4, 40000), "free at expiry")
        val s5 = LockoutPolicy.record(s4, 40000, false)
        ok(s5 == LockState(5, 100000), "fifth 60s")
        var s = s0
        repeat(10) { s = LockoutPolicy.record(s, 0, false) }
        ok(s.failures == 10 && s.lockedUntilMillis == 1920000L, "saturate 32min")
        val s11 = LockoutPolicy.record(s, 0, false)
        ok(s11 == s, "saturate stable")
        ok(LockoutPolicy.record(s5, 99999, true) == LockState(0, 0), "success resets")
        ok(!LockoutPolicy.locked(s0, 0), "fresh unlocked")
        fails("negative now") { LockoutPolicy.record(s0, -1, false) }
        fails("bad state") { LockoutPolicy.record(LockState(11, 0), 0, false) }
    }

    fun cue(text: String) {
        val sheet = CueSheetParser.parse(text)
        ok(sheet.file == "sample.bin", "file name")
        ok(sheet.tracks.size == 3, "3 tracks")
        ok(sheet.tracks[0] == CueTrack(1, "First Song", "Sample Artist", 0), "track 1")
        ok(sheet.tracks[1] == CueTrack(2, "Second Song", null, 15450), "track 2 skips INDEX 00")
        ok(sheet.tracks[2] == CueTrack(3, null, null, 32250), "track 3 bare")
        fails("missing FILE") { CueSheetParser.parse("TRACK 01 AUDIO\nINDEX 01 00:00:00\n") }
        fails("track before FILE") { CueSheetParser.parse("TRACK 01 AUDIO\nFILE \"a\" BINARY\n") }
        fails("first not 1") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 02 AUDIO\nINDEX 01 00:00:00\n") }
        fails("noncontig") {
            CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 03 AUDIO\nINDEX 01 01:00:00\n")
        }
        fails("missing INDEX 01") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nTITLE \"t\"\n") }
        fails("dup INDEX 01") {
            CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nINDEX 01 00:01:00\n")
        }
        fails("nonincreasing") {
            CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nINDEX 01 01:00:00\nTRACK 02 AUDIO\nINDEX 01 01:00:00\n")
        }
        fails("bad frames") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:00:75\n") }
        fails("bad seconds") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:60:00\n") }
        fails("unknown keyword") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nTEMPO 120\nINDEX 01 00:00:00\n") }
        fails("lower keyword") { CueSheetParser.parse("FILE \"a\" BINARY\ntrack 01 audio\nINDEX 01 00:00:00\n") }
        fails("unquoted title") { CueSheetParser.parse("FILE \"a\" BINARY\nTRACK 01 AUDIO\nTITLE bare\nINDEX 01 00:00:00\n") }
        fails("no tracks") { CueSheetParser.parse("FILE \"a\" BINARY\n") }
        fails("dup FILE") { CueSheetParser.parse("FILE \"a\" BINARY\nFILE \"b\" BINARY\n") }
    }
}
